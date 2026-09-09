/*
 * Fails the image build if the packaged jar cannot do the first thing the app does (MCO-560).
 *
 * The bug this exists for was invisible to every test: `jar-with-dependencies` overwrote
 * flyway-core's `META-INF/services` file with flyway-database-postgresql's, so the packaged
 * jar had a half-empty Flyway PluginRegister and threw NullPointerException on the boot-time
 * schema validation. Tests run on a normal classpath, where both files still exist side by
 * side, so they passed while production crashlooped for a day.
 *
 * Two properties follow from that, and both are load-bearing here:
 *
 *   - It must run against the ASSEMBLED jar, not the reactor classpath. That is why this is a
 *     Docker build step and not a JUnit test — a test would run before `package` and assert
 *     against a stale or absent artifact, which is a green light that checks nothing.
 *   - It must not need a database. `DriverDataSource`'s constructor is where the NPE was
 *     thrown, and it resolves the driver without opening a connection, so an unreachable host
 *     exercises the real failure. Do not "improve" this into a connection test.
 *
 * Run by webapp/Dockerfile via the single-file source launcher.
 */
import org.flywaydb.core.Flyway;

public class VerifyFatJar {
    public static void main(String[] args) {
        Flyway.configure()
                .dataSource("jdbc:postgresql://verify-fat-jar.invalid:5432/none", "none", "none")
                .locations("classpath:db/migration")
                .load();
        System.out.println("VerifyFatJar: Flyway configured off the packaged jar - services merged.");
    }
}
