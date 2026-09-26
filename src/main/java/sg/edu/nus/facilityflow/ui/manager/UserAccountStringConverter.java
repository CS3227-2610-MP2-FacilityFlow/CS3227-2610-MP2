package sg.edu.nus.facilityflow.ui.manager;

import javafx.util.StringConverter;
import sg.edu.nus.facilityflow.model.UserAccount;

final class UserAccountStringConverter extends StringConverter<UserAccount> {
    @Override
    public String toString(UserAccount account) {
        return account == null ? "" : account.displayName() + " (" + account.username() + ")";
    }

    @Override
    public UserAccount fromString(String value) {
        throw new UnsupportedOperationException("Accounts must be selected from the list");
    }
}
