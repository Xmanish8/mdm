# GitHub submission

No repository has been created, initialized in your E: folder, or pushed by this assistant.

1. Extract the enhanced source into a new folder and confirm the application works with your local database.
2. Run .\mvnw.cmd test. Optionally enable MDM_DB_TESTS against a dedicated test database as described in README.md.
3. Review gitignore. Keep database passwords in environment variables, never in application.properties or a committed .env file.
4. In the folder containing pom.xml, run:

   git init
   git add .
   git commit -m "Build MDM operations workspace"
   git branch -M main

5. Create an empty repository in your GitHub account. Copy its actual repository URL and use it with git remote add origin, then run git push -u origin main. Do not use a made-up URL or overwrite another repository.
6. Verify the Java 21 tests workflow completes in the Actions tab. It runs the database-independent test suite. MySQL tests are deliberately skipped there because no database service or secrets are configured.
7. Submit the repository link with README.md, ENGINEERING_DECISIONS.md, TEST_REPORT.md, setup instructions and known limitations included.

If you already have a Git repository, use its existing history and remote instead of initializing a replacement. The .github/workflows/tests.yml file is supplied, not remotely executed.

Production CI additionally checks common source credential patterns and builds the Docker image. It does not publish an image, deploy a server, create secrets or prove the final container is vulnerability-free.
