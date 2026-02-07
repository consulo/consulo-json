package consulo.json.jom.proxy;

import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import consulo.annotation.access.RequiredReadAction;
import consulo.json.jom.JomElement;
import consulo.json.jom.JomUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author VISTALL
 * @since 13.11.2015
 */
public class JomProxyInvocationHandler implements InvocationHandler {
    @Nonnull
    public static JomElement createProxy(
        @Nonnull Class<?> interfaceClass,
        @Nullable JsonObject objectLiteralExpression
    ) {
        return (JomElement)Proxy.newProxyInstance(
            interfaceClass.getClassLoader(),
            new Class[]{interfaceClass},
            new JomProxyInvocationHandler(objectLiteralExpression)
        );
    }

    private JsonObject myObjectLiteralExpression;

    public JomProxyInvocationHandler(@Nullable JsonObject objectLiteralExpression) {
        myObjectLiteralExpression = objectLiteralExpression;
    }

    @Override
    @RequiredReadAction
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String propertyName = JomUtil.getJsonGetPropertyName(method);
        if (propertyName != null) {
            JsonProperty jsProperty = findProperty(myObjectLiteralExpression, propertyName);
            if (jsProperty == null) {
                return JomValueConverter.getDefaultValueForType(method.getReturnType());
            }
            else {
                try {
                    return JomValueConverter.convertToObject(method.getReturnType(), method.getGenericReturnType(), jsProperty.getValue());
                }
                catch (JomBadValueExpressionException e) {
                    return JomValueConverter.getDefaultValueForType(method.getReturnType());
                }
            }
        }
        return null;
    }

    @Nullable
    private static JsonProperty findProperty(@Nullable JsonObject objectLiteralExpression, String name) {
        if (objectLiteralExpression == null) {
            return null;
        }

        return objectLiteralExpression.findProperty(name);
    }
}
