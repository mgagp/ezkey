# Hibernate JBoss Logging - Liste de Référence pour Native Image

## Contexte

Hibernate utilise JBoss Logging avec des classes générées à la compilation. Ces classes doivent être explicitement enregistrées pour la réflexion en natif.

## Liste Complète des Loggers Hibernate (Référence)

### Loggers Core Hibernate
- `org.hibernate.internal.CoreMessageLogger` + `CoreMessageLogger_$logger`
- `org.hibernate.internal.EntityManagerMessageLogger` + `EntityManagerMessageLogger_$logger`
- `org.hibernate.internal.log.DeprecationLogger` + `DeprecationLogger_$logger`
- `org.hibernate.internal.CoreLogging`

### Loggers JDBC/Connection
- `org.hibernate.engine.jdbc.env.internal.LobCreationLogging` + `LobCreationLogging_$logger` ✅ (ajouté 2025-12-16)
- `org.hibernate.internal.log.ConnectionInfoLogger` + `ConnectionInfoLogger_$logger` ✅ (ajouté 2025-12-16)
- `org.hibernate.engine.jdbc.env.spi.JdbcEnvironment`

### Loggers Dialect
- `org.hibernate.dialect.DialectLogging` + `DialectLogging_$logger`

### Loggers SQL/Query
- `org.hibernate.engine.jdbc.spi.SqlStatementLogger` + `SqlStatementLogger_$logger`
- `org.hibernate.engine.query.spi.QueryPlanCacheLogging` + `QueryPlanCacheLogging_$logger`
- `org.hibernate.loader.LoaderLogging` + `LoaderLogging_$logger`

### Loggers Transaction
- `org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorBuilderImpl`
- `org.hibernate.resource.transaction.spi.TransactionLogging` + `TransactionLogging_$logger`

### Loggers Session/Entity
- `org.hibernate.internal.SessionLogging` + `SessionLogging_$logger`
- `org.hibernate.persister.entity.EntityPersisterLogging` + `EntityPersisterLogging_$logger`
- `org.hibernate.event.internal.EventLogging` + `EventLogging_$logger`

### Loggers Cache
- `org.hibernate.cache.spi.CacheLogging` + `CacheLogging_$logger`
- `org.hibernate.cache.internal.CacheLogging` + `CacheLogging_$logger`

### Loggers Metadata/Bootstrap
- `org.hibernate.boot.model.source.internal.hbm.HbmLocalMetadataBuilderImpl`
- `org.hibernate.boot.internal.MetadataBuilderImpl`
- `org.hibernate.boot.internal.MetadataBuilderImpl$MetadataBuildingOptionsImpl`

### Loggers Type System
- `org.hibernate.type.descriptor.java.JavaTypeDescriptorRegistry`
- `org.hibernate.type.spi.TypeLogging` + `TypeLogging_$logger`

### Loggers Strategy
- `org.hibernate.boot.model.relational.ColumnOrderingStrategyStandard`
- `org.hibernate.boot.registry.selector.internal.StrategySelectorImpl`

### Loggers Persister
- `org.hibernate.persister.entity.SingleTableEntityPersister`
- `org.hibernate.persister.entity.JoinedSubclassEntityPersister`
- `org.hibernate.persister.entity.UnionSubclassEntityPersister`

### Loggers Loader
- `org.hibernate.loader.ast.internal.SingleIdEntityLoaderStandardImpl`
- `org.hibernate.loader.ast.internal.CollectionLoaderSingleKey`

### Loggers Engine
- `org.hibernate.engine.spi.EntityEntryFactory$EntityEntryImpl`
- `org.hibernate.internal.HEMLogging`

## Pattern de Nommage

Tous les loggers JBoss Logging suivent le pattern :
- Interface : `org.hibernate.*.Logging` ou `org.hibernate.*.Logger`
- Implémentation générée : `*_$logger` (générée par annotation processor)

## Notes

- Cette liste est basée sur Hibernate 6.6.36.Final
- Certains loggers peuvent ne pas être nécessaires selon l'utilisation
- Les loggers sont découverts progressivement lors de l'initialisation
- Chaque version de Hibernate peut avoir des loggers différents

## Statut Actuel dans ezkey-auth-api

### ✅ Déjà ajoutés (itératif)
1. `LobCreationLogging` + `LobCreationLogging_$logger` (2025-12-16)
2. `ConnectionInfoLogger` + `ConnectionInfoLogger_$logger` (2025-12-16)

### ⏳ À découvrir (itératif)
- Loggers qui seront découverts lors des prochaines erreurs

### 📋 Liste complète (one-shot)
- Tous les loggers listés ci-dessus (si approche one-shot choisie)
