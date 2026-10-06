/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Starts the whole application. This needs REAL services (PostgreSQL and Redis, from your environment
 * variables) and runs with ddl-auto=update, so it can change a real database. Run it only against a
 * throwaway/test database, never your production one. That is why it is not part of the normal build.
 *
 * Replaces the old WhereIsMyBusApplicationTests, which ran on every `mvn test` and failed without a database.
 * Note: when run with -Pextra-tests, src/test-extra/resources/application.properties applies, which has
 * no real database. To use this class, point DB_URL etc. at a test database and replace that file.
 */
@SpringBootTest
class WhereIsMyBusApplicationIT {

    @Test
    void contextLoads() {
    }
}
