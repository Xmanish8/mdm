import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCrypt;

/** Writes a fresh review workspace setup; never connects to or changes a database. */
class ReviewerSetup {
    static String literal(String value){return "CONVERT(0x"+HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8))+" USING utf8mb4)";}
    static String random(int bytes){byte[] value=new byte[bytes];new SecureRandom().nextBytes(value);return HexFormat.of().formatHex(value);}
    static Properties profiles(Path root)throws Exception{
        Properties data=new Properties();
        try(var input=Files.newInputStream(root.resolve("src/main/resources/reviewer-profiles.properties"))){data.load(input);}
        return data;
    }
    static String generate(Path root,String password,String databasePassword)throws Exception{
        if(password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72||!databasePassword.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid reviewer setup credentials");
        Properties data=profiles(root);
        StringBuilder sql=new StringBuilder("-- PRIVATE initialization file. Import ONCE as the Aiven management user.\n-- Only mdm_review and mdm_review_app are created; existing mdm data is untouched.\nCREATE DATABASE mdm_review CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\nUSE mdm_review;\n");
        sql.append(Files.readString(root.resolve("src/main/resources/schema.sql"))).append("\n");
        sql.append("CREATE TABLE reviewer_workspace (id INT PRIMARY KEY,marker VARCHAR(64) NOT NULL);\n")
           .append("INSERT INTO reviewer_workspace(id,marker) SELECT 1,'MDM_PUBLIC_REVIEW_ONLY' WHERE NOT EXISTS (SELECT 1 FROM users);\n")
           .append("START TRANSACTION;\nINSERT INTO teams(name) VALUES ('MDM Review Operations'),('MDM Review Engineering');\n");
        for(String id:data.getProperty("profiles").split(",")){
            String role=data.getProperty(id+".role");
            if(!Set.of("ADMIN","MEMBER").contains(role))throw new IllegalStateException("Unsupported reviewer role");
            sql.append("INSERT INTO users(name,email,password_hash,role) VALUES (")
               .append(literal(data.getProperty(id+".name"))).append(',').append(literal(data.getProperty(id+".email"))).append(',')
               .append(literal(BCrypt.hashpw(password,BCrypt.gensalt(12)))).append(",'"+role+"');\n");
            for(String team:data.getProperty(id+".memberships").split("\\|")){
                sql.append("INSERT INTO team_memberships(user_id,team_id) SELECT u.id,t.id FROM users u CROSS JOIN teams t WHERE u.email=")
                   .append(literal(data.getProperty(id+".email"))).append(" AND t.name=").append(literal(team)).append(";\n");
            }
        }
        sql.append("SET @review_ops=(SELECT id FROM teams WHERE name='MDM Review Operations');\nSET @review_eng=(SELECT id FROM teams WHERE name='MDM Review Engineering');\n");
        for(String id:new String[]{"manish","aditi","rahul","sneha"})sql.append("SET @review_").append(id).append("=(SELECT id FROM users WHERE email=").append(literal(data.getProperty(id+".email"))).append(");\n");
        String[][] tasks={
            {"Verify a delayed UPI refund","A fictional payment refund needs a status check.","URGENT","OPEN","ops","NULL","aditi","Check the payment reference"},
            {"Review access for a new joiner","Check the sample onboarding access list.","HIGH","IN_PROGRESS","ops","rahul","aditi","Confirm the required applications"},
            {"Resolve a courier pickup delay","The sample pickup is waiting for a tracking update.","MEDIUM","BLOCKED","ops","aditi","aditi","Wait for the tracking reference"},
            {"Approve the weekly operations checklist","The sample checklist is ready for administrator review.","LOW","PENDING_APPROVAL","ops","aditi","aditi","Review and approve the checklist"},
            {"Fix mobile navigation spacing","The sample navigation needs a check at narrow widths.","HIGH","OPEN","eng","NULL","sneha","Reproduce at a 360px viewport"},
            {"Improve task search response time","Review the sample search queries and filters.","MEDIUM","IN_PROGRESS","eng","sneha","sneha","Inspect query execution plans"},
            {"Review task reassignment validation","The sample validation change is ready for approval.","HIGH","PENDING_APPROVAL","eng","sneha","sneha","Verify team membership checks"},
            {"Add missing form field labels","Accessible labels were added to the sample forms.","LOW","RESOLVED","eng","sneha","sneha","Review completed"}
        };
        for(String[] task:tasks){
            sql.append("INSERT INTO work_items(title,description,priority,status,team_id,assigned_to,created_by,next_action) VALUES (")
               .append(literal(task[0])).append(',').append(literal(task[1])).append(',').append(literal(task[2])).append(',').append(literal(task[3]))
               .append(",@review_").append(task[4]).append(',').append(task[5].equals("NULL")?"NULL":"@review_"+task[5])
               .append(",@review_").append(task[6]).append(',').append(literal(task[7])).append(");\n");
        }
        sql.append("INSERT INTO activity_logs(work_item_id,actor_id,action,details) SELECT id,created_by,'CREATED','Fictional review data' FROM work_items;\nCOMMIT;\n");
        sql.append("CREATE USER 'mdm_review_app'@'%' IDENTIFIED BY '").append(databasePassword).append("' REQUIRE SSL;\n");
        for(String line:Files.readAllLines(root.resolve("deploy/02-permissions.sh"))){
            if(line.startsWith("GRANT ")&&line.contains("TO 'mdm_app'"))sql.append(line.replace("mdm.","mdm_review.").replace("'mdm_app'","'mdm_review_app'")).append('\n');
        }
        sql.append("SHOW GRANTS FOR 'mdm_review_app'@'%';\nSELECT name,email,role FROM mdm_review.users;\n");
        return sql.toString();
    }
    public static void main(String[] args)throws Exception{
        if(args.length==2&&args[0].equals("--self-test")){
            String sql=generate(Path.of(args[1]),"test-only-reviewer-password","0".repeat(64));
            if(sql.contains("test-only-reviewer-password")||sql.contains("DROP ")||sql.contains("UPDATE mdm.")||!sql.contains("CREATE USER 'mdm_review_app'"))throw new IllegalStateException("Reviewer setup self-check failed");
            long profiles=sql.lines().filter(line->line.startsWith("INSERT INTO users(")).count();
            long tasks=sql.lines().filter(line->line.startsWith("INSERT INTO work_items(")).count();
            long grants=sql.lines().filter(line->line.startsWith("GRANT ")).count();
            if(profiles!=4||tasks!=8||grants!=12)throw new IllegalStateException("Reviewer setup content is incomplete");
            System.out.println("Reviewer setup self-check passed: 4 accounts, 8 tasks, restricted grants. No files created.");return;
        }
        if(args.length!=2)throw new IllegalArgumentException("Pass project root and private output directory");
        Path root=Path.of(args[0]);Path output=Path.of(args[1]);
        for(String name:List.of("reviewer.password","reviewer-db.password","reviewer-bootstrap.sql"))
            if(Files.exists(output.resolve(name)))throw new IllegalStateException("Existing reviewer setup preserved; do not regenerate credentials for an existing workspace");
        String password="MdmReview-"+random(6);String dbPassword=random(32);
        String sql=generate(root,password,dbPassword);
        Files.writeString(output.resolve("reviewer.password"),password,StandardOpenOption.CREATE_NEW);
        Files.writeString(output.resolve("reviewer-db.password"),dbPassword,StandardOpenOption.CREATE_NEW);
        Files.writeString(output.resolve("reviewer-bootstrap.sql"),sql,StandardOpenOption.CREATE_NEW);
        System.out.println("Private reviewer setup files prepared. Existing database and administrator credentials were not changed.");
    }
}
