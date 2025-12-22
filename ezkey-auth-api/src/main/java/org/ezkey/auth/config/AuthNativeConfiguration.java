/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AuthNativeConfiguration
 *
 * Description: Native image configuration and AOT hints for Ezkey Auth API.
 */

package org.ezkey.auth.config;

import org.ezkey.authattempt.dto.AuthAttemptPendingRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptPendingResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondResponseDto;
import org.ezkey.enrollment.dto.EnrollmentBindRequestDto;
import org.ezkey.enrollment.dto.EnrollmentBindResponseDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyRequestDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyResponseDto;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Native image configuration for Ezkey Auth API.
 *
 * <p>This configuration provides AOT hints and runtime configuration needed for native image
 * compilation. It includes reflection configuration for DTOs, resource access patterns, and other
 * native image requirements.
 *
 * <p><b>AOT Processing:</b>
 *
 * <ul>
 *   <li><b>Reflection:</b> All DTOs used in REST endpoints
 *   <li><b>Resources:</b> Application properties and validation messages
 *   <li><b>Serialization:</b> Jackson serialization for all DTOs
 *   <li><b>Proxies:</b> JDK proxies for JBoss Logging interfaces
 * </ul>
 *
 * <p><b>Configuration Approach:</b>
 *
 * <p>This class uses Java-based configuration via {@link RuntimeHintsRegistrar} for all hints
 * supported by Spring AOT API (reflection, resources, serialization, proxies). However, some
 * GraalVM build options (like {@code --initialize-at-run-time}) cannot be configured via the Spring
 * AOT API and must be specified in {@code native-image.properties}. This is a limitation of the
 * Spring AOT API, not a design choice.
 *
 * <p>For GraalVM build options, see: {@code
 * src/main/resources/META-INF/native-image/org.ezkey/ezkey-auth-api/native-image.properties}
 *
 * @since 2025
 */
@Configuration
@ImportRuntimeHints(AuthNativeConfiguration.AuthRuntimeHints.class)
public class AuthNativeConfiguration {

  /**
   * Runtime hints registrar for native image compilation.
   *
   * <p>Registers all DTOs and classes that need reflection access during native image runtime. This
   * includes all request/response DTOs used by the REST endpoints.
   */
  static class AuthRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      hints
          .reflection() //
          .registerType(org.ezkey.authattempt.domain.entity.AuthAttempt.class) //
          .registerType(org.ezkey.enrollment.domain.entity.Enrollment.class) //
          .registerType(org.ezkey.integration.domain.entity.Integration.class);

      // Register enrollment DTOs for reflection
      hints
          .reflection() //
          .registerType(EnrollmentBindRequestDto.class) //
          .registerType(EnrollmentBindResponseDto.class) //
          .registerType(EnrollmentVerifyRequestDto.class) //
          .registerType(EnrollmentVerifyResponseDto.class);

      // Register auth attempt DTOs for reflection
      hints
          .reflection() //
          .registerType(AuthAttemptPendingRequestDto.class) //
          .registerType(AuthAttemptPendingResponseDto.class) //
          .registerType(AuthAttemptRespondRequestDto.class) //
          .registerType(AuthAttemptRespondResponseDto.class);

      // Register serialization hints for Jackson - using TypeReference
      hints
          .serialization() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(EnrollmentBindRequestDto.class)) //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(EnrollmentBindResponseDto.class)) //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(EnrollmentVerifyRequestDto.class)) //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(EnrollmentVerifyResponseDto.class)) //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(AuthAttemptPendingRequestDto.class)) //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  AuthAttemptPendingResponseDto.class)) //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(AuthAttemptRespondRequestDto.class)) //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(AuthAttemptRespondResponseDto.class));

      hints
          .serialization() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.authattempt.domain.entity.AuthAttempt.class)) //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.enrollment.domain.entity.Enrollment.class)) //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  org.ezkey.integration.domain.entity.Integration.class));

      // MapStruct generated implementations (e.g. *MapperImpl) must be present in native images.
      // If the native image build prunes these classes, Spring AOT-generated bean definitions will
      // fail at runtime with NoClassDefFoundError.
      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.authattempt.mapper.AuthAttemptMapperImpl"),
              hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)) //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.ezkey.enrollment.mapper.EnrollmentAuthMapperImpl"),
              hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));

      // Register resource patterns
      hints
          .resources() //
          .registerPattern("application*.properties") //
          .registerPattern("META-INF/native-image/org.ezkey/ezkey-auth-api/*") //
          .registerPattern("ValidationMessages.properties") //
          // Hibernate DTD/XSD resources - required for XML mapping resolution
          // Register specific DTD/XSD files that Hibernate needs
          .registerPattern("org/hibernate/hibernate-mapping-3.0.dtd") //
          .registerPattern("org/hibernate/hibernate-configuration-3.0.dtd") //
          .registerPattern("org/hibernate/hibernate-mapping-4.0.xsd") //
          .registerPattern("org/hibernate/hibernate-configuration-4.0.xsd") //
          // Also register patterns for any other DTD/XSD files in subdirectories
          .registerPattern("org/hibernate/**/*.dtd") //
          .registerPattern("org/hibernate/**/*.xsd");

      // Register Caffeine cache classes for reflection
      // Caffeine generates classes dynamically based on cache configuration
      // Class names follow pattern: [S|P][S|L][M|W][S|A][W|A] where:
      // S=Strong, P=Probabilistic, L=Linked, M=Manual, W=Window, A=Access
      // We register all possible combinations for the cache configuration used
      String[] caffeineGeneratedClasses = {
        // Strong-Strong-Manual-Strong-Access (SSMSA) - used for maximumSize + expireAfterAccess
        "com.github.benmanes.caffeine.cache.SSMSA",
        // Probabilistic-Strong-Access-Manual-Strong (PSAMS) - another configuration variant
        "com.github.benmanes.caffeine.cache.PSAMS",
        // Other common combinations
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
        // Additional variants
        "com.github.benmanes.caffeine.cache.PSAM",
        "com.github.benmanes.caffeine.cache.PSAMW",
        "com.github.benmanes.caffeine.cache.PSMA",
        "com.github.benmanes.caffeine.cache.PSMS",
        "com.github.benmanes.caffeine.cache.PSMW"
      };

      for (String className : caffeineGeneratedClasses) {
        hints
            .reflection() //
            .registerType(
                org.springframework.aot.hint.TypeReference.of(className),
                hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
      }

      // Register Caffeine core classes
      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "com.github.benmanes.caffeine.cache.Caffeine"),
              hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS)) //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "com.github.benmanes.caffeine.cache.Cache"),
              hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS)) //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "com.github.benmanes.caffeine.cache.LocalCacheFactory"),
              hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS));

      // Register Hibernate and JBoss Logging classes for reflection
      // JBoss Logging generates logger implementations via annotation processors
      // These classes must be registered for reflection in native images

      String[] hibernateJbossLoggingClasses = {
        // Hibernate core classes
        "org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl",
        "org.hibernate.internal.HEMLogging",
        "org.hibernate.engine.spi.EntityEntryFactory$EntityEntryImpl",
        "org.hibernate.persister.entity.SingleTableEntityPersister",
        "org.hibernate.persister.entity.JoinedSubclassEntityPersister",
        "org.hibernate.persister.entity.UnionSubclassEntityPersister",
        // Collection persisters are resolved via reflection in native images
        "org.hibernate.persister.collection.OneToManyPersister",
        "org.hibernate.loader.ast.internal.SingleIdEntityLoaderStandardImpl",
        "org.hibernate.loader.ast.internal.CollectionLoaderSingleKey",
        "org.hibernate.type.descriptor.java.JavaTypeDescriptorRegistry",
        // JBoss Logging generated classes - these are created by annotation processors
        // The exact names depend on which Hibernate classes use @MessageLogger
        // Core Hibernate logger (most important - used throughout Hibernate)
        "org.hibernate.internal.CoreMessageLogger",
        "org.hibernate.internal.CoreMessageLogger_$logger",
        // EntityManager logger
        "org.hibernate.internal.EntityManagerMessageLogger",
        "org.hibernate.internal.EntityManagerMessageLogger_$logger",
        // Deprecation logger
        "org.hibernate.internal.log.DeprecationLogger",
        "org.hibernate.internal.log.DeprecationLogger_$logger",
        // Dialect logger
        "org.hibernate.dialect.DialectLogging",
        "org.hibernate.dialect.DialectLogging_$logger",
        // LOB Creation logger - required for JDBC environment initialization
        "org.hibernate.engine.jdbc.env.internal.LobCreationLogging",
        "org.hibernate.engine.jdbc.env.internal.LobCreationLogging_$logger",
        // Connection Info logger - required for JDBC connection logging
        "org.hibernate.internal.log.ConnectionInfoLogger",
        "org.hibernate.internal.log.ConnectionInfoLogger_$logger",
        // JDBC batch logger - required for BatchBuilderImpl initialization
        "org.hibernate.engine.jdbc.batch.JdbcBatchLogging",
        "org.hibernate.engine.jdbc.batch.JdbcBatchLogging_$logger",
        // Bytecode enhancement interceptor logger - required during entity persister initialization
        "org.hibernate.bytecode.enhance.spi.interceptor.BytecodeInterceptorLogging",
        "org.hibernate.bytecode.enhance.spi.interceptor.BytecodeInterceptorLogging_$logger",
        // Mapping model creation logger - required during Hibernate metamodel initialization
        "org.hibernate.metamodel.mapping.MappingModelCreationLogging",
        "org.hibernate.metamodel.mapping.MappingModelCreationLogging_$logger",
        // Query logger - required for query interpretation cache initialization
        "org.hibernate.query.QueryLogging",
        "org.hibernate.query.QueryLogging_$logger",
        // SQL AST tree logger - required for SQL AST tree printing and translation
        // Note: The implementation class SqlAstTreeLogger_$logger is generated by JBoss Logging
        // and must be included in the native image. If not found, ensure the generated class
        // is present in the Hibernate JAR and not pruned by GraalVM.
        "org.hibernate.sql.ast.tree.SqlAstTreeLogger",
        "org.hibernate.sql.ast.tree.SqlAstTreeLogger_$logger",
        // SQL AST tree printer - uses SqlAstTreeLogger
        "org.hibernate.sql.ast.SqlTreePrinter",
        // SQL AST translator - may also use SqlAstTreeLogger
        "org.hibernate.sql.ast.spi.AbstractSqlAstTranslator",
        // CoreLogging helper class
        "org.hibernate.internal.CoreLogging",
        "org.hibernate.engine.jdbc.env.spi.JdbcEnvironment",
        "org.hibernate.boot.model.source.internal.hbm.HbmLocalMetadataBuilderImpl",
        // Hibernate strategy classes - instantiated via reflection by StrategySelectorImpl
        "org.hibernate.boot.model.relational.ColumnOrderingStrategyStandard",
        "org.hibernate.boot.registry.selector.internal.StrategySelectorImpl",
        "org.hibernate.boot.internal.MetadataBuilderImpl",
        "org.hibernate.boot.internal.MetadataBuilderImpl$MetadataBuildingOptionsImpl",
        // Hibernate EventType builds a standard event type map using reflection in native images
        "org.hibernate.event.spi.EventType",
        // Identifier generators instantiated via reflection (e.g. GenerationType.IDENTITY)
        "org.hibernate.id.IdentityGenerator",
        // Default generator used when identifiers are assigned manually (no @GeneratedValue)
        "org.hibernate.id.Assigned",
        // Hibernate Bean Validation integration - loaded via Class.forName in native image
        "org.hibernate.boot.beanvalidation.TypeSafeActivator",
        // Hibernate Validator JBoss Logging - required for HV initialization in native images
        "org.hibernate.validator.internal.util.logging.Log",
        "org.hibernate.validator.internal.util.logging.Log_$logger",
        "org.hibernate.validator.internal.util.logging.Messages",
        "org.hibernate.validator.internal.util.logging.Messages_$bundle",
        // Hibernate dialect and transaction coordinator - instantiated via reflection
        "org.hibernate.dialect.PostgreSQLDialect",
        // PostgreSQL JDBC Types - required for PostgreSQL-specific type handling
        "org.hibernate.dialect.PostgreSQLInetJdbcType",
        "org.hibernate.dialect.PostgreSQLIntervalSecondJdbcType",
        "org.hibernate.dialect.PostgreSQLStructPGObjectJdbcType",
        "org.hibernate.dialect.PostgreSQLJsonPGObjectJsonbType",
        "org.hibernate.resource.transaction.backend.jdbc.internal."
            + "JdbcResourceLocalTransactionCoordinatorBuilderImpl",
        // Hibernate event listeners - required for event system initialization
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
            .reflection() //
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

      // SqlAstTreeLogger: Multi-layered approach for JBoss Logging generated classes
      //
      // Problem: JBoss Logging generates implementation classes (e.g., SqlAstTreeLogger_$logger)
      // at compile time. These classes may not be properly initialized in native images,
      // causing "implementation not found" errors at runtime.
      //
      // Solution: We use a multi-layered approach:
      // 1. Reflection registration (above) - ensures classes are included in native image
      // 2. Conditional registration (below) - register if present on classpath
      // 3. Proxy registration - alternative mechanism to force inclusion
      // 4. Runtime initialization (native-image.properties) - defers initialization to runtime
      //
      // Note: Spring AOT RuntimeHints API does NOT support GraalVM build options like
      // --initialize-at-run-time. These must be configured in native-image.properties.
      // This is a limitation of the Spring AOT API, not a design choice.

      // Try to register the generated implementation class if it exists
      hints
          .reflection() //
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
          .proxies() //
          .registerJdkProxy(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.sql.ast.tree.SqlAstTreeLogger"));

      // Register Hibernate event listener array types - required for event system
      // Hibernate allocates arrays of event listeners reflectively (e.g. AutoFlushEventListener[])
      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.AutoFlushEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.PersistEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.DeleteEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.DirtyCheckEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.EvictEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.FlushEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.FlushEntityEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.LoadEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.ResolveNaturalIdEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.InitializeCollectionEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.LockEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.MergeEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.PreLoadEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.PreInsertEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.PreUpdateEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.PreDeleteEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.PreUpsertEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.PreCollectionUpdateEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.PostLoadEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.PostUpsertEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.SaveOrUpdateEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.RefreshEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.event.spi.ReplicateEventListener[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      // Register Hibernate SQL AST array types - required for native image SQL AST translator.
      // Hibernate allocates Statement[] reflectively (Array.newInstance) during mutation
      // translation.
      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of(
                  "org.hibernate.sql.ast.tree.Statement[]"),
              hint -> hint.withMembers(MemberCategory.UNSAFE_ALLOCATED));

      // Register JBoss Logging infrastructure
      hints
          .reflection() //
          .registerType(
              org.springframework.aot.hint.TypeReference.of("org.jboss.logging.Logger"),
              hint ->
                  hint.withMembers(
                      MemberCategory.INVOKE_DECLARED_METHODS,
                      MemberCategory.INVOKE_PUBLIC_METHODS)) //
          .registerType(
              org.springframework.aot.hint.TypeReference.of("org.jboss.logging.Logger$Level"),
              hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS)) //
          .registerType(
              org.springframework.aot.hint.TypeReference.of("org.jboss.logging.Messages"),
              hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS)) //
          .registerType(
              org.springframework.aot.hint.TypeReference.of("org.jboss.logging.MessageLogger"),
              hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS));
    }
  }
}
