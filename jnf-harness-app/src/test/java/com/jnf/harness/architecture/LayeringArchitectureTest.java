package com.jnf.harness.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 分层门禁：DDD 六边形的依赖方向由测试守护，不靠自觉。
 * <p>依赖方向（与计划第 0 节一致）：
 * <pre>
 * App → Trigger → {API, Case, Infrastructure}
 * Case → {Domain, API}
 * Infrastructure → Domain
 * Domain → Types（契约包，零框架依赖）
 * </pre>
 * 规则故意违反会变红（验收见 plan/第一阶段 第 0 节 DoD）。
 */
class LayeringArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.jnf");

    @Test
    @DisplayName("domain 不得依赖 infrastructure/cases/trigger/app（领域层是内核）")
    void domain_must_not_depend_on_outer_layers() {
        noClasses().that().resideInAPackage("com.jnf.harness.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.jnf.harness.infrastructure..",
                        "com.jnf.harness.cases..",
                        "com.jnf.harness.trigger..",
                        "com.jnf.harness.app..")
                .check(classes);
    }

    @Test
    @DisplayName("api（门面契约）不得依赖 domain/case/infrastructure/trigger/app")
    void api_must_stay_framework_only() {
        noClasses().that().resideInAPackage("com.jnf.harness.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.jnf.harness.domain..",
                        "com.jnf.harness.cases..",
                        "com.jnf.harness.infrastructure..",
                        "com.jnf.harness.trigger..",
                        "com.jnf.harness.app..")
                .check(classes);
    }

    @Test
    @DisplayName("cases（用例编排）不得依赖 infrastructure/trigger/app")
    void cases_must_not_depend_on_adapters() {
        noClasses().that().resideInAPackage("com.jnf.harness.cases..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.jnf.harness.infrastructure..",
                        "com.jnf.harness.trigger..",
                        "com.jnf.harness.app..")
                .check(classes);
    }

    @Test
    @DisplayName("infrastructure（端口实现）不得依赖 cases/trigger/app")
    void infrastructure_must_not_depend_on_use_cases() {
        noClasses().that().resideInAPackage("com.jnf.harness.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.jnf.harness.cases..",
                        "com.jnf.harness.trigger..",
                        "com.jnf.harness.app..")
                .check(classes);
    }

    @Test
    @DisplayName("trigger 不得依赖 app（接入层不感知启动器）")
    void trigger_must_not_depend_on_app() {
        noClasses().that().resideInAPackage("com.jnf.harness.trigger..")
                .should().dependOnClassesThat().resideInAPackage("com.jnf.harness.app..")
                .check(classes);
    }

    @Test
    @DisplayName("契约包（spi/model）零依赖：不得依赖本工程任何其他包")
    void types_contract_must_be_dependency_free() {
        noClasses().that().resideInAnyPackage(
                        "com.jnf.harness.spi..", "com.jnf.harness.model..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.jnf.harness.domain..",
                        "com.jnf.harness.cases..",
                        "com.jnf.harness.infrastructure..",
                        "com.jnf.harness.trigger..",
                        "com.jnf.harness.app..",
                        "com.jnf.harness.api..")
                .check(classes);
    }
}
