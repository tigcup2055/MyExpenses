package org.totschnig.myexpenses.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CODE
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.viewmodel.data.Currency
import java.text.Collator
import java.util.*

open class CurrencyViewModel(application: Application) : ContentResolvingAndroidViewModel(application) {

    private val currencies = MutableLiveData<List<Currency>>()

    val default: Currency
        get() = Currency.create(Utils.getHomeCurrency().code())


    fun getCurrencies(): LiveData<List<Currency>> {
        return currencies
    }

    fun loadCurrencies() {
        val collator: Collator? = try {
            Collator.getInstance()
        } catch (e: Exception) {
            CrashHandler.report(e)
            null
        }
        disposable = briteContentResolver.createQuery(TransactionProvider.CURRENCIES_URI, null, null, null, KEY_CODE, true)
                .mapToList { Currency.create(it) }
                .subscribe { currencies ->
                    if (collator != null) {
                        Collections.sort(currencies) { lhs, rhs ->
                            val classCompare = Utils.compare(lhs.sortClass(), rhs.sortClass())
                            if (classCompare == 0)
                                collator.compare(lhs.toString(), rhs.toString())
                            else
                                classCompare
                        }
                    }
                    this.currencies.postValue(currencies)
                }
    }
}
