package com.clinic.doc_appointment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Builds the schema from the Flyway baseline and then makes Hibernate <em>validate</em> the entities
 * against it.
 *
 * <p>This is the check that matters for {@code V1__baseline_schema.sql}: production now runs
 * {@code ddl-auto: validate}, so a single missing or misnamed column in the baseline is the
 * difference between a service that starts and one that does not. Hibernate comparing every entity
 * against the migrated schema catches that mechanically — a hand-review would not.
 *
 * <p><strong>Scope, stated plainly:</strong> this runs on H2 in PostgreSQL compatibility mode, so it
 * verifies that the baseline's tables, columns and types line up with the entity model. It does
 * <em>not</em> prove the DDL is valid Postgres, and it deliberately stops at V1
 * ({@code flyway.target=1}) because V2 is PL/pgSQL that H2 cannot parse. Both migrations still need
 * one run against a real Postgres before deploying.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        // Stop at the baseline: V2 uses DO $$ ... $$ blocks, which are Postgres-only.
        "spring.flyway.target=1",
        "spring.flyway.baseline-on-migrate=false",
        // The point of the test: Hibernate must agree with what the migration created.
        "spring.jpa.hibernate.ddl-auto=validate",
        // A per-test database, so Flyway starts from empty rather than a schema another test built.
        "spring.datasource.url=jdbc:h2:mem:baseline_check;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL"
})
class BaselineMigrationMatchesEntitiesTest {

    /**
     * An empty body is the whole test: if the baseline and the entity model disagree, Hibernate's
     * schema validation fails and the context never loads.
     */
    @Test
    void contextLoadsWithSchemaBuiltFromTheBaselineMigration() {
    }
}
