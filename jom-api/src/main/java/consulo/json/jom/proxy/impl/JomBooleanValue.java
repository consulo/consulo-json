package consulo.json.jom.proxy.impl;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.JsonLiteral;
import consulo.annotation.access.RequiredReadAction;
import consulo.json.jom.proxy.JomBadValueExpressionException;
import consulo.json.jom.proxy.JomValueConverter;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.reflect.Type;

/**
 * @author VISTALL
 * @since 13.11.2015
 */
public class JomBooleanValue implements JomValueConverter.Converter<Boolean> {
    private Boolean myDefaultValue;

    public JomBooleanValue(@Nullable Boolean defaultValue) {
        myDefaultValue = defaultValue;
    }

    @Override
    public Boolean getDefaultValue() {
        return myDefaultValue;
    }

    @RequiredReadAction
    @Override
    public Boolean parseValue(
        @Nonnull Class type,
        @Nonnull Type genericType,
        @Nonnull PsiElement value
    ) throws JomBadValueExpressionException {
        if (value instanceof JsonLiteral) {
            IElementType elementType = PsiUtilCore.getElementType(value.getFirstChild());
            if (elementType == JsonElementTypes.TRUE) {
                return Boolean.TRUE;
            }
            else if (elementType == JsonElementTypes.FALSE) {
                return Boolean.FALSE;
            }
        }
        throw new JomBadValueExpressionException();
    }
}
