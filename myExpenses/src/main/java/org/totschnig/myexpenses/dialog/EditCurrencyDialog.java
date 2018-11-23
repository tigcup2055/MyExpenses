package org.totschnig.myexpenses.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.model.CurrencyContext;
import org.totschnig.myexpenses.model.CurrencyUnit;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.util.form.FormFieldNotEmptyValidator;
import org.totschnig.myexpenses.util.form.FormValidator;
import org.totschnig.myexpenses.util.form.NumberRangeValidator;
import org.totschnig.myexpenses.viewmodel.EditCurrencyViewModel;
import org.totschnig.myexpenses.viewmodel.data.Currency;

import java.util.Locale;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;

import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CURRENCY;

public class EditCurrencyDialog extends CommitSafeDialogFragment {

  @BindView(R.id.edt_currency_symbol)
  EditText editTextSymbol;

  @BindView(R.id.edt_currency_fraction_digits)
  EditText editTextFractionDigits;

  @BindView(R.id.edt_currency_code)
  EditText editTextCode;

  @BindView(R.id.edt_currency_label)
  EditText editTextLabel;

  @BindView(R.id.container_currency_label)
  ViewGroup containerLabel;

  @BindView(R.id.container_currency_code)
  ViewGroup containerCode;

  @BindView(R.id.checkBox)
  CheckBox checkBox;

  @BindView(R.id.warning_change_fraction_digits)
  TextView warning;

  @Inject
  CurrencyContext currencyContext;

  private EditCurrencyViewModel editCurrencyViewModel;

  public static EditCurrencyDialog newInstance(Currency currency) {
    Bundle arguments = new Bundle(1);
    arguments.putSerializable(KEY_CURRENCY, currency);
    EditCurrencyDialog editCurrencyDialog = new EditCurrencyDialog();
    editCurrencyDialog.setArguments(arguments);
    return editCurrencyDialog;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    MyApplication.getInstance().getAppComponent().inject(this);
    editCurrencyViewModel = ViewModelProviders.of(this).get(EditCurrencyViewModel.class);
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    Activity ctx = getActivity();
    LayoutInflater li = LayoutInflater.from(ctx);
    //noinspection InflateParams
    View view = li.inflate(R.layout.edit_currency, null);
    ButterKnife.bind(this, view);
    Currency currency = getCurrency();
    CurrencyUnit currencyUnit = currencyContext.get(currency.code());
    editTextSymbol.setText(currencyUnit.symbol());
    editTextFractionDigits.setText(String.valueOf(currencyUnit.fractionDigits()));
    editTextFractionDigits.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {

      }

      @Override
      public void afterTextChanged(Editable s) {
        final int newValue = readFractionDigitsFromUI();
        final int oldValue = currentFractionDigits();
        final boolean valueUpdate = newValue != -1 && newValue != oldValue;
        checkBox.setVisibility(valueUpdate ? View.VISIBLE : View.GONE);
        warning.setVisibility(valueUpdate ? View.VISIBLE : View.GONE);
        if (valueUpdate) {
          String message = getString(R.string.warning_change_fraction_digits_1);
          int delta = oldValue - newValue;
          message += " " + getString(
              delta > 0 ? R.string.warning_change_fraction_digits_2_multiplied :
                  R.string.warning_change_fraction_digits_2_divided,
              Utils.pow(10, Math.abs(delta)));
          if (delta > 0) {
            message += " " + getString(R.string.warning_change_fraction_digits_3);
          }
          warning.setText(message);
        }
      }
    });
    editTextCode.setText(currency.code());
    final String displayName = currency.toString();
    final boolean frameworkCurrency = isFrameworkCurrency(currency.code());
    if (frameworkCurrency) {
      editTextSymbol.requestFocus();
    } else {
      containerLabel.setVisibility(View.VISIBLE);
      containerCode.setVisibility(View.VISIBLE);
      editTextLabel.setText(displayName);
    }
    final AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
        .setView(view)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok, null);
    if (frameworkCurrency) {
      builder.setTitle(String.format(Locale.ROOT, "%s (%s)", displayName, currency.code()));
    }
    AlertDialog alertDialog = builder.create();
    alertDialog.setOnShowListener(dialog -> {

      Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
      button.setOnClickListener(this::onOkClick);
    });
    return alertDialog;
  }

  private String currentSymbol() {
    return currencyContext.get(getCurrency().code()).symbol();
  }

  private String readSymbolfromUI() {
    return editTextSymbol.getText().toString();
  }

  private int currentFractionDigits() {
    return currencyContext.get(getCurrency().code()).fractionDigits();
  }

  private int readFractionDigitsFromUI() {
    try {
      return Integer.parseInt(editTextFractionDigits.getText().toString());
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private boolean isFrameworkCurrency(String currencyCode) {
    try {
      final java.util.Currency instance = java.util.Currency.getInstance(currencyCode);
      return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && instance.getNumericCode() != 0;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private Currency getCurrency() {
    return (Currency) getArguments().getSerializable(KEY_CURRENCY);
  }

  private void onOkClick(View view) {
    FormValidator validator = new FormValidator();
    validator.add(new FormFieldNotEmptyValidator(editTextSymbol));
    validator.add(new NumberRangeValidator(editTextFractionDigits, 0, 8));
    if (validator.validate()) {
      final Currency currency = getCurrency();
      final String symbol = readSymbolfromUI();
      if (!symbol.equals(currentSymbol())) {
        editCurrencyViewModel.saveSymbol(currency, symbol);
      }
      int numberFractionDigits = readFractionDigitsFromUI();
      if (numberFractionDigits != currentFractionDigits()) {
        boolean withUpdate = checkBox.isChecked();
        editCurrencyViewModel.saveFractionDigits(currency, numberFractionDigits, withUpdate);
      }
      dismiss();
    }
  }
}
