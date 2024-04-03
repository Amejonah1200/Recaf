package software.coley.recaf.services.config.factories;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import software.coley.observables.Observable;
import software.coley.recaf.config.ConfigContainer;
import software.coley.recaf.config.ConfigValue;
import software.coley.recaf.services.config.TypedConfigComponentFactory;
/**
 * Factory for general {@link String} values.
 *
 * @author pvpb0t
 */
@ApplicationScoped
public class StringComponentFactory extends TypedConfigComponentFactory<String> {

    @Inject
    protected StringComponentFactory() {
        super(false, String.class);
    }

    @Nonnull
    @Override
    public Node create(@Nonnull ConfigContainer container, @Nonnull ConfigValue<String> value) {
        Observable<String> observable = value.getObservable();
        TextField textField = new TextField();
        textField.setText(observable.getValue());
        textField.textProperty().addListener((observableValue, oldValue, newValue) -> {
            observable.setValue(newValue);
        });
        return textField;
    }
}
