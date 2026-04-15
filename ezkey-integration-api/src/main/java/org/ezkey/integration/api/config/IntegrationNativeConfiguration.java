/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: IntegrationNativeConfiguration
 *
 * Description: Native image configuration and AOT hints for Ezkey Integration API.
 */

package org.ezkey.integration.api.config;

import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitResponseDto;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.http.ProblemDetail;

/**
 * Native image configuration for Ezkey Integration API.
 *
 * <p>This configuration keeps the Integration API on the same native posture as Auth API: Java
 * runtime hints first, GraalVM build flags in {@code native-image.properties}, and third-party
 * metadata delegated to the Graal reachability repository where available.
 *
 * @since 2025
 */
@Configuration
@ImportRuntimeHints(IntegrationNativeConfiguration.IntegrationRuntimeHints.class)
public class IntegrationNativeConfiguration {

  /** Registers the reflection, serialization, and resource hints needed by Integration API. */
  static class IntegrationRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      hints
          .reflection()
          .registerType(org.ezkey.authattempt.domain.entity.AuthAttempt.class)
          .registerType(org.ezkey.enrollment.domain.entity.Enrollment.class)
          .registerType(org.ezkey.integration.domain.entity.Integration.class)
          .registerType(AuthAttemptCreateRequestDto.class)
          .registerType(AuthAttemptCreateResponseDto.class)
          .registerType(AuthAttemptDto.class)
          .registerType(AuthAttemptWaitRequestDto.class)
          .registerType(AuthAttemptWaitResponseDto.class)
          .registerType(ProblemDetail.class);

      hints
          .serialization()
          .registerType(
              org.springframework.aot.hint.TypeReference.of(AuthAttemptCreateRequestDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(AuthAttemptCreateResponseDto.class))
          .registerType(org.springframework.aot.hint.TypeReference.of(AuthAttemptDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(AuthAttemptWaitRequestDto.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(AuthAttemptWaitResponseDto.class))
          .registerType(org.springframework.aot.hint.TypeReference.of(ProblemDetail.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.authattempt.domain.entity.AuthAttempt.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.enrollment.domain.entity.Enrollment.class))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.integration.domain.entity.Integration.class));

      hints
          .reflection()
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.authattempt.mapper.AuthAttemptIntegrationApiMapperImpl"),
              hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));

      hints
          .resources()
          .registerPattern("application*.properties")
          .registerPattern("META-INF/native-image/org.ezkey/ezkey-integration-api/*")
          .registerPattern("ValidationMessages.properties")
          .registerPattern("org/hibernate/hibernate-mapping-3.0.dtd")
          .registerPattern("org/hibernate/hibernate-configuration-3.0.dtd")
          .registerPattern("org/hibernate/hibernate-mapping-4.0.xsd")
          .registerPattern("org/hibernate/hibernate-configuration-4.0.xsd")
          .registerPattern("org/hibernate/**/*.dtd")
          .registerPattern("org/hibernate/**/*.xsd");

      String[] caffeineGeneratedClasses = {
        "com.github.benmanes.caffeine.cache.SSMSA",
        "com.github.benmanes.caffeine.cache.PSAMS",
        "com.github.benmanes.caffeine.cache.PSW",
        "com.github.benmanes.caffeine.cache.PSWMS",
        "com.github.benmanes.caffeine.cache.SSLA",
        "com.github.benmanes.caffeine.cache.SSLMSW",
        "com.github.benmanes.caffeine.cache.SSMSW",
        "com.github.benmanes.caffeine.cache.PSWA",
        "com.github.benmanes.caffeine.cache.PSWMSA",
        "com.github.benmanes.caffeine.cache.SSLMSA",
        "com.github.benmanes.caffeine.cache.SSMS",
        "com.github.benmanes.caffeine.cache.SSMW",
        "com.github.benmanes.caffeine.cache.SSMA",
        "com.github.benmanes.caffeine.cache.PSMSA",
        "com.github.benmanes.caffeine.cache.PSMSW",
        "com.github.benmanes.caffeine.cache.PSLMSA",
        "com.github.benmanes.caffeine.cache.PSLMSW",
        "com.github.benmanes.caffeine.cache.PSAM",
        "com.github.benmanes.caffeine.cache.PSAMW",
        "com.github.benmanes.caffeine.cache.PSMA",
        "com.github.benmanes.caffeine.cache.PSMS",
        "com.github.benmanes.caffeine.cache.PSMW"
      };

      for (String className : caffeineGeneratedClasses) {
        hints
            .reflection()
            .registerType(
                org.springframework.aot.hint.TypeReference.of(className),
                hint ->
                    hint.withMembers(
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.ACCESS_DECLARED_FIELDS));
      }

      hints
          .reflection()
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "com.github.benmanes.caffeine.cache.Caffeine"),
              hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "com.github.benmanes.caffeine.cache.Cache"),
              hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS))
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "com.github.benmanes.caffeine.cache.LocalCacheFactory"),
              hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS));

      String[] hibernateJbossLoggingClasses = {
        "org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl",
        "org.hibernate.internal.HEMLogging",
        "org.hibernate.engine.spi.EntityEntryFactory$EntityEntryImpl",
        "org.hibernate.persister.entity.SingleTableEntityPersister",
        "org.hibernate.persister.entity.JoinedSubclassEntityPersister",
        "org.hibernate.persister.entity.UnionSubclassEntityPersister",
        "org.hibernate.persister.collection.OneToManyPersister",
        "org.hibernate.loader.ast.internal.SingleIdEntityLoaderStandardImpl",
        "org.hibernate.loader.ast.internal.CollectionLoaderSingleKey",
        "org.hibernate.type.descriptor.java.JavaTypeDescriptorRegistry",
        "org.hibernate.internal.CoreMessageLogger",
        "org.hibernate.internal.CoreMessageLogger_$logger",
        "org.hibernate.boot.BootLogging",
        "org.hibernate.boot.BootLogging_$logger",
        "org.hibernate.jpa.internal.JpaLogger",
        "org.hibernate.jpa.internal.JpaLogger_$logger",
        "org.hibernate.internal.EntityManagerMessageLogger",
        "org.hibernate.internal.EntityManagerMessageLogger_$logger",
        "org.hibernate.internal.log.DeprecationLogger",
        "org.hibernate.internal.log.DeprecationLogger_$logger",
        "org.hibernate.dialect.DialectLogging",
        "org.hibernate.dialect.DialectLogging_$logger",
        "org.hibernate.engine.jdbc.env.internal.LobCreationLogging",
        "org.hibernate.engine.jdbc.env.internal.LobCreationLogging_$logger",
        "org.hibernate.internal.log.ConnectionInfoLogger",
        "org.hibernate.internal.log.ConnectionInfoLogger_$logger",
        "org.hibernate.engine.jdbc.batch.JdbcBatchLogging",
        "org.hibernate.engine.jdbc.batch.JdbcBatchLogging_$logger",
        "org.hibernate.bytecode.enhance.spi.interceptor.BytecodeInterceptorLogging",
        "org.hibernate.bytecode.enhance.spi.interceptor.BytecodeInterceptorLogging_$logger",
        "org.hibernate.metamodel.mapping.MappingModelCreationLogging",
        "org.hibernate.metamodel.mapping.MappingModelCreationLogging_$logger",
        "org.hibernate.query.QueryLogging",
        "org.hibernate.query.QueryLogging_$logger",
        "org.hibernate.sql.ast.tree.SqlAstTreeLogger",
        "org.hibernate.sql.ast.tree.SqlAstTreeLogger_$logger",
        "org.hibernate.sql.ast.SqlTreePrinter",
        "org.hibernate.sql.ast.spi.AbstractSqlAstTranslator",
        "org.hibernate.internal.CoreLogging",
        "org.hibernate.engine.jdbc.env.spi.JdbcEnvironment",
        "org.hibernate.boot.model.source.internal.hbm.HbmLocalMetadataBuilderImpl",
        "org.hibernate.boot.model.relational.ColumnOrderingStrategyStandard",
        "org.hibernate.boot.registry.selector.internal.StrategySelectorImpl",
        "org.hibernate.boot.internal.MetadataBuilderImpl",
        "org.hibernate.boot.internal.MetadataBuilderImpl$MetadataBuildingOptionsImpl",
        "org.hibernate.event.spi.EventType",
        "org.hibernate.id.IdentityGenerator",
        "org.hibernate.id.Assigned",
        "org.hibernate.boot.beanvalidation.TypeSafeActivator",
        "org.hibernate.validator.internal.util.logging.Log",
        "org.hibernate.validator.internal.util.logging.Log_$logger",
        "org.hibernate.validator.internal.util.logging.Messages",
        "org.hibernate.validator.internal.util.logging.Messages_$bundle",
        "org.hibernate.dialect.PostgreSQLDialect",
        "org.hibernate.dialect.PostgreSQLInetJdbcType",
        "org.hibernate.dialect.PostgreSQLIntervalSecondJdbcType",
        "org.hibernate.dialect.PostgreSQLStructPGObjectJdbcType",
        "org.hibernate.dialect.PostgreSQLJsonPGObjectJsonbType",
        "org.hibernate.resource.transaction.backend.jdbc.internal."
            + "JdbcResourceLocalTransactionCoordinatorBuilderImpl",
        "org.hibernate.event.spi.AutoFlushEventListener",
        "org.hibernate.event.spi.PersistEventListener",
        "org.hibernate.event.spi.DeleteEventListener",
        "org.hibernate.event.spi.DirtyCheckEventListener",
        "org.hibernate.event.spi.EvictEventListener",
        "org.hibernate.event.spi.FlushEventListener",
        "org.hibernate.event.spi.FlushEntityEventListener",
        "org.hibernate.event.spi.LoadEventListener",
        "org.hibernate.event.spi.ResolveNaturalIdEventListener",
        "org.hibernate.event.spi.InitializeCollectionEventListener",
        "org.hibernate.event.spi.LockEventListener",
        "org.hibernate.event.spi.MergeEventListener",
        "org.hibernate.event.spi.PreLoadEventListener",
        "org.hibernate.event.spi.PreInsertEventListener",
        "org.hibernate.event.spi.PreUpdateEventListener",
        "org.hibernate.event.spi.PreDeleteEventListener",
        "org.hibernate.event.spi.PreUpsertEventListener",
        "org.hibernate.event.spi.PreCollectionUpdateEventListener",
        "org.hibernate.event.spi.PostLoadEventListener",
        "org.hibernate.event.spi.PostUpsertEventListener",
        "org.hibernate.event.spi.SaveOrUpdateEventListener",
        "org.hibernate.event.spi.RefreshEventListener",
        "org.hibernate.event.spi.ReplicateEventListener",
      };

      for (String className : hibernateJbossLoggingClasses) {
        hints
            .reflection()
            .registerType(
                org.springframework.aot.hint.TypeReference.of(className),
                hint ->
                    hint.withMembers(
                        MemberCategory.INTROSPECT_PUBLIC_CONSTRUCTORS,
                        MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.DECLARED_FIELDS));
      }

      String[] hibernateGeneratedLoggerClasses = {
        "org.hibernate.action.internal.ActionLogging_$logger",
        "org.hibernate.boot.BootLogging_$logger",
        "org.hibernate.boot.archive.scan.internal.ScannerLogger_$logger",
        "org.hibernate.boot.beanvalidation.BeanValidationLogger_$logger",
        "org.hibernate.boot.jaxb.JaxbLogger_$logger",
        "org.hibernate.bytecode.enhance.internal.BytecodeEnhancementLogging_$logger",
        "org.hibernate.bytecode.enhance.spi.interceptor.BytecodeInterceptorLogging_$logger",
        "org.hibernate.cache.spi.SecondLevelCacheLogger_$logger",
        "org.hibernate.collection.internal.CollectionLogger_$logger",
        "org.hibernate.context.internal.CurrentSessionLogging_$logger",
        "org.hibernate.dialect.DialectLogging_$logger",
        "org.hibernate.engine.internal.NaturalIdLogging_$logger",
        "org.hibernate.engine.internal.PersistenceContextLogging_$logger",
        "org.hibernate.engine.internal.SessionMetricsLogger_$logger",
        "org.hibernate.engine.internal.VersionLogger_$logger",
        "org.hibernate.engine.jdbc.JdbcLogging_$logger",
        "org.hibernate.engine.jdbc.batch.JdbcBatchLogging_$logger",
        "org.hibernate.engine.jdbc.connections.internal.ConnectionProviderLogging_$logger",
        "org.hibernate.engine.jdbc.env.internal.LobCreationLogging_$logger",
        "org.hibernate.engine.jdbc.spi.SQLExceptionLogging_$logger",
        "org.hibernate.event.internal.EntityCopyLogging_$logger",
        "org.hibernate.event.internal.EventListenerLogging_$logger",
        "org.hibernate.id.UUIDLogger_$logger",
        "org.hibernate.id.enhanced.OptimizerLogger_$logger",
        "org.hibernate.id.enhanced.SequenceGeneratorLogger_$logger",
        "org.hibernate.id.enhanced.TableGeneratorLogger_$logger",
        "org.hibernate.internal.CoreMessageLogger_$logger",
        "org.hibernate.internal.SessionFactoryLogging_$logger",
        "org.hibernate.internal.SessionFactoryRegistryMessageLogger_$logger",
        "org.hibernate.internal.SessionLogging_$logger",
        "org.hibernate.internal.log.ConnectionAccessLogger_$logger",
        "org.hibernate.internal.log.ConnectionInfoLogger_$logger",
        "org.hibernate.internal.log.DeprecationLogger_$logger",
        "org.hibernate.internal.log.IncubationLogger_$logger",
        "org.hibernate.internal.log.StatisticsLogger_$logger",
        "org.hibernate.internal.log.UrlMessageBundle_$logger",
        "org.hibernate.jpa.internal.JpaLogger_$logger",
        "org.hibernate.loader.ast.internal.MultiKeyLoadLogging_$logger",
        "org.hibernate.metamodel.mapping.MappingModelCreationLogging_$logger",
        "org.hibernate.query.QueryLogging_$logger",
        "org.hibernate.query.hql.HqlLogging_$logger",
        "org.hibernate.resource.beans.internal.BeansMessageLogger_$logger",
        "org.hibernate.resource.jdbc.internal.LogicalConnectionLogging_$logger",
        "org.hibernate.resource.jdbc.internal.ResourceRegistryLogger_$logger",
        "org.hibernate.resource.transaction.backend.jta.internal.JtaLogging_$logger",
        "org.hibernate.resource.transaction.internal.SynchronizationLogging_$logger",
        "org.hibernate.service.internal.ServiceLogger_$logger",
        "org.hibernate.sql.ast.tree.SqlAstTreeLogger_$logger",
        "org.hibernate.sql.exec.SqlExecLogger_$logger",
        "org.hibernate.sql.model.ModelMutationLogging_$logger",
        "org.hibernate.sql.results.LoadingLogger_$logger",
        "org.hibernate.sql.results.ResultsLogger_$logger",
        "org.hibernate.sql.results.graph.embeddable.EmbeddableLoadingLogger_$logger"
      };

      for (String className : hibernateGeneratedLoggerClasses) {
        hints
            .reflection()
            .registerTypeIfPresent(
                classLoader,
                className,
                hint ->
                    hint.withMembers(
                        MemberCategory.INTROSPECT_PUBLIC_CONSTRUCTORS,
                        MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.DECLARED_FIELDS));
      }

      hints
          .reflection()
          .registerTypeIfPresent(
              classLoader,
              "org.hibernate.sql.ast.tree.SqlAstTreeLogger_$logger",
              hint ->
                  hint.withMembers(
                      MemberCategory.INTROSPECT_PUBLIC_CONSTRUCTORS,
                      MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS,
                      MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                      MemberCategory.INVOKE_DECLARED_METHODS,
                      MemberCategory.INVOKE_PUBLIC_METHODS,
                      MemberCategory.DECLARED_FIELDS));

      hints
          .proxies()
          .registerJdkProxy(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.sql.ast.tree.SqlAstTreeLogger"));

      String[] hibernateEventListenerArrays = {
        "org.hibernate.event.spi.AutoFlushEventListener[]",
        "org.hibernate.event.spi.PersistEventListener[]",
        "org.hibernate.event.spi.DeleteEventListener[]",
        "org.hibernate.event.spi.DirtyCheckEventListener[]",
        "org.hibernate.event.spi.EvictEventListener[]",
        "org.hibernate.event.spi.FlushEventListener[]",
        "org.hibernate.event.spi.FlushEntityEventListener[]",
        "org.hibernate.event.spi.LoadEventListener[]"
      };

      for (String className : hibernateEventListenerArrays) {
        hints
            .reflection()
            .registerType(
                org.springframework.aot.hint.TypeReference.of(className),
                hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));
      }
    }
  }
}
