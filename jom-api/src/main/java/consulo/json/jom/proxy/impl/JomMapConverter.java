package consulo.json.jom.proxy.impl;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import jakarta.annotation.Nonnull;
import consulo.annotation.access.RequiredReadAction;
import consulo.json.jom.proxy.JomBadValueExpressionException;
import consulo.json.jom.proxy.JomValueConverter;
import consulo.language.psi.PsiElement;
import consulo.util.lang.Pair;

/**
 * @author VISTALL
 * @since 13.11.2015
 */
public class JomMapConverter implements JomValueConverter.Converter<Map> {
    @Override
    public Map getDefaultValue() {
        return Collections.emptyMap();
    }

    @RequiredReadAction
    @Override
    @SuppressWarnings("unchecked")
    public Map parseValue(@Nonnull Class type, @Nonnull Type genericType, @Nonnull PsiElement value) throws JomBadValueExpressionException {
        if (!(value instanceof JsonObject jsonObject)) {
            throw new JomBadValueExpressionException();
        }

        Pair<Class, Type> valueType = JomCollectionValue.findValueTypeInsideGeneric(genericType, 1); // K, V

        Map map = new LinkedHashMap();

        for (JsonProperty property : jsonObject.getPropertyList()) {
            String name = property.getName();
            if (name == null) {
                continue;
            }

            try {
                Object object = JomValueConverter.convertToObject(valueType.getFirst(), valueType.getSecond(), property.getValue());
                map.put(name, object);
            }
            catch (JomBadValueExpressionException e) {
                // we dont interest in bad value
            }
        }

        return map;
    }
}
