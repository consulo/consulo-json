package consulo.json.jom.proxy.impl;

import com.intellij.json.psi.JsonStringLiteral;
import consulo.annotation.access.RequiredReadAction;
import consulo.json.jom.proxy.JomBadValueExpressionException;
import consulo.json.jom.proxy.JomValueConverter;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

import java.lang.reflect.Type;

/**
 * @author VISTALL
 * @since 13.11.2015
 */
public class JomStringConverter implements JomValueConverter.Converter<String> {
    @Override
    public String getDefaultValue() {
        return null;
    }

    @RequiredReadAction
    @Override
    public String parseValue(
        @Nonnull Class type,
        @Nonnull Type genericType,
        @Nonnull PsiElement value
    ) throws JomBadValueExpressionException {
        if (value instanceof JsonStringLiteral stringLiteral) {
            return stringLiteral.getValue();
        }

        throw new JomBadValueExpressionException();
    }
}
