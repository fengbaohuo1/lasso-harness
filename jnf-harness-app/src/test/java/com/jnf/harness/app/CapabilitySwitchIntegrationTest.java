package com.jnf.harness.app;

import com.jnf.harness.domain.tool.service.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2 能力开关验收：关闭的子系统零注册（工具不注册、服务 Bean 不建、REST 控制器不挂）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = JnfHarnessApplication.class,
        properties = {"harness.auth.api-keys=test-key",
                "harness.llm.seed-default-channel=false",
                "harness.capabilities.jobs=false",
                "harness.capabilities.a2a=false",
                "harness.capabilities.collaboration=false",
                "harness.events.dir=target/test-events-caps",
                "spring.datasource.url=jdbc:h2:mem:dbcaps;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
class CapabilitySwitchIntegrationTest {

    @Autowired
    ToolRegistry toolRegistry;

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("关闭 jobs/a2a/collaboration：工具不注册、服务 Bean 不建、控制器不在")
    void disabled_capabilities_are_not_registered() {
        assertFalse(toolRegistry.lookup("job_run").isPresent(), "jobs 关闭后工具不应注册");
        assertFalse(toolRegistry.lookup("job_list").isPresent());
        assertTrue(toolRegistry.lookup("ask_user_question").isPresent(), "未关的能力不受影响");
        assertFalse(context.containsBean("a2aTaskService"), "a2a 服务 Bean 不应创建");
        assertFalse(context.containsBean("collaborationService"), "collaboration 服务 Bean 不应创建");
        assertTrue(context.containsBean("harnessExecutionService"), "未关的 task 服务照常装配");
    }
}
