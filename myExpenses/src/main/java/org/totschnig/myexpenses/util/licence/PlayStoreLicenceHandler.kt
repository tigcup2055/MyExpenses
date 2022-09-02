package org.totschnig.myexpenses.util.licence

import android.app.Application
import androidx.annotation.VisibleForTesting
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.google.android.vending.licensing.PreferenceObfuscator
import org.totschnig.myexpenses.activity.IapActivity
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import java.util.*

open class PlayStoreLicenceHandler(
    context: Application,
    preferenceObfuscator: PreferenceObfuscator,
    crashHandler: CrashHandler,
    prefHandler: PrefHandler
) : AbstractInAppPurchaseLicenceHandler(context, preferenceObfuscator, crashHandler, prefHandler) {
    private fun storeSkuDetails(inventory: List<ProductDetails>) {
        val editor = pricesPrefs.edit()
        for (productDetails in inventory) {
            val price = if (productDetails.productType == ProductType.INAPP) {
                productDetails.oneTimePurchaseOfferDetails!!.formattedPrice
            } else { //SUBS
                productDetails.subscriptionOfferDetails!![0].pricingPhases.pricingPhaseList[0].formattedPrice
            }
            editor.putString(prefKeyForSkuPrice(productDetails.productId), price)
        }
        editor.apply()
    }

    private fun prefKeyForSkuPrice(sku: String): String {
        return String.format(Locale.ROOT, "%s_price", sku)
    }

    override fun getDisplayPriceForPackage(aPackage: Package) =
        getPriceFromPrefs(getSkuForPackage(aPackage))

    @Suppress("MemberVisibilityCanBePrivate")
    fun getPriceFromPrefs(sku: String) =
        pricesPrefs.getString(prefKeyForSkuPrice(sku), null)

    /**
     * Pair of sku and purchaseToken
     */
    private val currentSubscription: Pair<String, String>?
        get() {
            val sku = licenseStatusPrefs.getString(KEY_CURRENT_SUBSCRIPTION_SKU, null)
            val purchaseToken =
                licenseStatusPrefs.getString(KEY_CURRENT_SUBSCRIPTION_PURCHASE_TOKEN, null)
            return if (sku != null && purchaseToken != null) Pair(sku, purchaseToken) else null
        }

    override fun initBillingManager(activity: IapActivity, query: Boolean): BillingManager {
        val billingUpdatesListener: BillingUpdatesListener = object : BillingUpdatesListener {
            override fun onPurchasesUpdated(
                purchases: List<Purchase>?,
                newPurchase: Boolean
            ): Boolean {
                if (purchases != null) {
                    val oldStatus = licenceStatus
                    val oldFeatures = addOnFeatures.size
                    registerInventory(purchases, newPurchase)

                    if (newPurchase || oldStatus != licenceStatus || addOnFeatures.size > oldFeatures) {
                        activity.onLicenceStatusSet(prettyPrintStatus(activity)
                        )
                    }
                }
                return licenceStatus != null || addOnFeatures.isNotEmpty()
            }

            override fun onPurchaseCanceled() {
                log().i("onPurchasesUpdated() - user cancelled the purchase flow - skipping")
                activity.onPurchaseCancelled()
            }

            override fun onPurchaseFailed(resultCode: Int) {
                log().w("onPurchasesUpdated() got unknown resultCode: %s", resultCode)
                activity.onPurchaseFailed(resultCode)
            }
        }
        val skuDetailsResponseListener =
            if (query) ProductDetailsResponseListener { result: BillingResult, productDetails: List<ProductDetails> ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    storeSkuDetails(productDetails)
                } else {
                    log().w("skuDetails response %d", result.responseCode)
                }
            } else null
        return BillingManagerPlay(activity, billingUpdatesListener, skuDetailsResponseListener)
    }

    @VisibleForTesting
    fun findHighestValidPurchase(inventory: List<Purchase>) = inventory.mapNotNull { purchase ->
        extractLicenceStatusFromSku(purchase.products[0])?.let {
            Pair(purchase, it)
        }
    }.maxByOrNull { pair -> pair.second }?.first

    private fun registerInventory(inventory: List<Purchase>, newPurchase: Boolean) {
        inventory.forEach { purchase: Purchase ->
            log().i(
                "%s (acknowledged %b)",
                purchase.products.joinToString(),
                purchase.isAcknowledged
            )
        }
        findHighestValidPurchase(inventory)?.also {
            if (it.purchaseState == Purchase.PurchaseState.PURCHASED) {
                handlePurchaseForLicence(it.products[0], it.orderId, it.purchaseToken)
            } else {
                //TODO handle pending
                CrashHandler.report(
                    Exception("Found purchase in state ${it.purchaseState}"),
                    TAG
                )
            }
        } ?: run {
            if (!newPurchase) {
                maybeCancel()
            }
        }
        handlePurchaseForAddOns(
            inventory.flatMap { it.products }.mapNotNull { Licence.parseFeature(it) },
            newPurchase
        )
        licenseStatusPrefs.commit()
    }

    private fun handlePurchaseForAddOns(
        features: List<AddOnPackage>,
        newPurchase: Boolean
    ) {
        maybeUpgradeAddonFeatures(features.map { it.feature }, newPurchase)
    }

    override suspend fun launchPurchase(
        aPackage: Package,
        shouldReplaceExisting: Boolean,
        billingManager: BillingManager
    ) {
        val sku = getSkuForPackage(aPackage)
        val oldPurchase = if (shouldReplaceExisting) {
            currentSubscription.also {
                checkNotNull(it) { "Could not determine current subscription" }
                check(sku != it.first)
            }!!.second
        } else {
            null
        }
        val type = if (aPackage is ProfessionalPackage) ProductType.SUBS else ProductType.INAPP
        (billingManager as BillingManagerPlay).initiatePurchaseFlow(sku, type, oldPurchase)
    }
}