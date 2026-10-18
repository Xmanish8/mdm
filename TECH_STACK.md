# Technology verification

| Component | Implementation | Status |
|---|---|---|
| Language | pom.xml java.version=21; Maven compiles with release 21 | Java 21 target; local checks ran on Java 25 |
| Database | MySQL Connector/J and MySQL schema | Implemented; live verification requires DB_PASSWORD |
| Database access | JdbcTemplate, parameterized SQL and Spring @Transactional | Spring JDBC; no JPA/Hibernate |
| Backend | Spring Boot 4.1.1, MVC controllers, service layer | Implemented |
| Frontend | HTML, Thymeleaf, Bootstrap 5.3.8 CSS and custom CSS | Implemented in enhanced version |
| Authentication | Spring Security form login, BCrypt, session, CSRF | Implemented |
| Authorization | Admin URL rules plus current database role and resource membership checks | Implemented |
| Tests | JUnit Jupiter, Spring Boot Test, MockMvc and Mockito | Default tests run without database; MySQL tests opt-in |
| Editor | Standard Maven folder usable in IntelliJ IDEA or VS Code | Your choice; not a runtime dependency |
| Submission | GitHub Actions Java 21 workflow and Git instructions | Prepared; no repository has been created or pushed |

Bootstrap is self-hosted in this production package; the version originated from https://getbootstrap.com/docs/5.3/getting-started/introduction/ and retains its MIT license. No frontend framework or separate Node build is required.

For an honest project explanation: say "Java 21-compatible source, MySQL, Spring JDBC, Spring Boot MVC, Thymeleaf with Bootstrap, Spring Security and JUnit/Spring Boot tests." Do not say the live MySQL tests or GitHub CI passed until you run them.
