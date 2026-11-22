package za.co.mamadise.support_ticket_tracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Structural guarantees over every mapped endpoint, so a future controller cannot quietly open a way to choose a
 * role: no registration or sign-up path, and no request body with a role field outside /api/admin.
 */
@SpringBootTest
class RoleAssignmentRestrictionsTest {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    private List<String> patterns(RequestMappingInfo info) {
        return new ArrayList<>(info.getPathPatternsCondition().getPatternValues());
    }

    @Test
    void thereIsNoPublicRegistrationEndpoint() {
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            for (String pattern : patterns(info)) {
                assertThat(pattern.toLowerCase()).doesNotContain("register").doesNotContain("signup")
                        .doesNotContain("sign-up");
            }
        }
    }

    @Test
    void onlyAdministratorEndpointsAcceptARoleInTheRequestBody() {
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            Method method = entry.getValue().getMethod();
            for (Parameter parameter : method.getParameters()) {
                if (parameter.isAnnotationPresent(RequestBody.class) && declaresRole(parameter.getType())) {
                    assertThat(patterns(entry.getKey())).allMatch(p -> p.startsWith("/api/admin/"),
                            method + " accepts a role but is not an administrator endpoint");
                }
            }
        }
    }

    @Test
    void theLoginBodyHasNoRoleField() {
        assertThat(declaresRole(LoginRequest.class)).isFalse();
    }

    private static boolean declaresRole(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).anyMatch(f -> f.getName().equalsIgnoreCase("role"));
    }
}
