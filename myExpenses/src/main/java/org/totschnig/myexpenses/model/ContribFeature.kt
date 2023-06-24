/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.totschnig.myexpenses.model

import android.content.Context
import androidx.core.text.HtmlCompat
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.licence.LicenceHandler
import org.totschnig.myexpenses.util.licence.LicenceHandler.Companion.TRIAL_DURATION_DAYS
import org.totschnig.myexpenses.util.licence.LicenceStatus
import java.util.Date

enum class ContribFeature constructor(
    val trialMode: TrialMode = TrialMode.DURATION,
    val licenceStatus: LicenceStatus = LicenceStatus.CONTRIB
) {
    ACCOUNTS_UNLIMITED(TrialMode.NONE) {
        override fun buildUsageLimitString(context: Context): String {
            val currentLicence = getCurrentLicence(context)
            return context.getString(
                R.string.dialog_contrib_usage_limit_accounts,
                FREE_ACCOUNTS,
                currentLicence
            )
        }
    },
    PLANS_UNLIMITED(TrialMode.NONE) {
        override fun buildUsageLimitString(context: Context): String {
            val currentLicence = getCurrentLicence(context)
            return context.getString(
                R.string.dialog_contrib_usage_limit_plans,
                FREE_PLANS,
                currentLicence
            )
        }
    },
    SPLIT_TRANSACTION,
    DISTRIBUTION,
    PRINT,
    AD_FREE(TrialMode.NONE),
    CSV_IMPORT(licenceStatus = LicenceStatus.EXTENDED),
    SYNCHRONIZATION(licenceStatus = LicenceStatus.EXTENDED),
    SPLIT_TEMPLATE(
        TrialMode.NONE,
        LicenceStatus.PROFESSIONAL
    ) {
        override fun buildUsageLimitString(context: Context): String {
            val currentLicence = getCurrentLicence(context)
            return context.getString(
                R.string.dialog_contrib_usage_limit_split_templates,
                currentLicence
            )
        }
    },
    PRO_SUPPORT(TrialMode.NONE, LicenceStatus.PROFESSIONAL),
    ROADMAP_VOTING(
        TrialMode.NONE,
        LicenceStatus.PROFESSIONAL
    ),
    HISTORY(licenceStatus = LicenceStatus.PROFESSIONAL),
    BUDGET(licenceStatus = LicenceStatus.PROFESSIONAL),
    OCR(licenceStatus = LicenceStatus.PROFESSIONAL),
    WEB_UI(licenceStatus = LicenceStatus.PROFESSIONAL),
    CATEGORY_TREE(TrialMode.UNLIMITED, LicenceStatus.PROFESSIONAL);

    enum class TrialMode {
        NONE, DURATION, UNLIMITED
    }

    override fun toString(): String {
        return name.lowercase()
    }

    val prefKey: String
        get() = "FEATURE_${name}_FIRST_USAGE"

    fun buildRequiresString(ctx: Context) =
        ctx.getString(R.string.contrib_key_requires, ctx.getString(licenceStatus.resId))

    val labelResId
        get() = when (this) {
            ACCOUNTS_UNLIMITED -> R.string.contrib_feature_accounts_unlimited_label
            PLANS_UNLIMITED -> R.string.contrib_feature_plans_unlimited_label
            SPLIT_TRANSACTION -> R.string.contrib_feature_split_transaction_label
            DISTRIBUTION -> R.string.contrib_feature_distribution_label
            PRINT -> R.string.contrib_feature_print_label
            AD_FREE -> R.string.contrib_feature_ad_free_label
            CSV_IMPORT -> R.string.contrib_feature_csv_import_label
            SYNCHRONIZATION -> R.string.contrib_feature_synchronization_label
            SPLIT_TEMPLATE -> R.string.contrib_feature_split_template_label
            PRO_SUPPORT -> R.string.contrib_feature_pro_support_label
            ROADMAP_VOTING -> R.string.contrib_feature_roadmap_voting_label
            HISTORY -> R.string.contrib_feature_history_label
            BUDGET -> R.string.contrib_feature_budget_label
            OCR -> R.string.contrib_feature_ocr_label
            WEB_UI -> R.string.contrib_feature_web_ui_label
            CATEGORY_TREE -> R.string.contrib_feature_category_tree_label
        }

    fun getLimitReachedWarning(ctx: Context): String {
        return ctx.getString(
            R.string.warning_trial_limit_reached,
            ctx.getString(labelResId)
        )
    }

    fun buildFullInfoString(ctx: Context): CharSequence {
        return HtmlCompat.fromHtml(
            ctx.getString(
                R.string.dialog_contrib_premium_feature,
                "<i>" + ctx.getString(labelResId) + "</i>",
                ctx.getString(licenceStatus.resId)
            ) + " " +
                    buildUsageLimitString(ctx), HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    fun buildUsagesLeftString(ctx: Context, licenceHandler: LicenceHandler): CharSequence? {
        return if (trialMode == TrialMode.DURATION) {
            val now = System.currentTimeMillis()
            val endOfTrial = licenceHandler.getEndOfTrial(this)
            if (endOfTrial < now) {
                getLimitReachedWarning(ctx)
            } else {
                ctx.getString(
                    R.string.warning_limited_trial, ctx.getString(labelResId),
                    Utils.getDateFormatSafe(ctx)
                        .format(Date(endOfTrial))
                )
            }
        } else null
    }

    open fun buildUsageLimitString(context: Context): String {
        val currentLicence = getCurrentLicence(context)
        return when (trialMode) {
            TrialMode.DURATION -> context.getString(
                R.string.dialog_contrib_usage_limit_synchronization,
                TRIAL_DURATION_DAYS,
                currentLicence
            )

            TrialMode.UNLIMITED -> context.getString(
                R.string.dialog_contrib_usage_limit_with_dialog,
                currentLicence
            )

            else -> ""
        }
    }

    protected fun getCurrentLicence(context: Context): String {
        val licenceStatus = context.injector.licenceHandler().licenceStatus
        return context.getString(licenceStatus?.resId ?: R.string.licence_status_free)
    }

    fun buildRemoveLimitation(ctx: Context, asHTML: Boolean): CharSequence {
        val resId = R.string.dialog_contrib_reminder_remove_limitation
        return if (asHTML) Utils.getTextWithAppName(ctx, resId) else ctx.getString(resId).replace(
            String.format("{%s}", Utils.PLACEHOLDER_APP_NAME), ctx.getString(R.string.app_name)
        )
    }

    val isExtended: Boolean
        get() = licenceStatus === LicenceStatus.EXTENDED
    val isProfessional: Boolean
        get() = licenceStatus === LicenceStatus.PROFESSIONAL

    fun trialButton(): Int {
        return if (trialMode == TrialMode.UNLIMITED) R.string.dialog_remind_later else R.string.dialog_contrib_no
    }

    companion object {
        const val FREE_PLANS = 3
        const val FREE_ACCOUNTS = 5
        const val FREE_SPLIT_TEMPLATES = 1
    }
}