import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.security.crypto.bcrypt.BCrypt;

/** Creates a private initialization file; it never connects to a database. */
class RenderAdmin {
    static String literal(String value) {
        return "CONVERT(0x"+HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8))+" USING utf8mb4)";
    }
    static String sql(String name,String email,String hash) {
        if(name==null||name.isBlank()||name.length()>100||name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Enter a name of 1–100 characters without control characters.");
        if(email==null||email.length()>150||!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Enter a valid email address.");
        return "INSERT INTO users(name,email,password_hash,role) VALUES ("+literal(name.trim())+","+
            literal(email.trim().toLowerCase(Locale.ROOT))+","+literal(hash)+",'ADMIN');\n";
    }
    static void selfTest() {
        String password="test-only-render-password";
        String hash=BCrypt.hashpw(password,BCrypt.gensalt(12));
        String statement=sql("Manish O'Marathe","MANISH@example.com",hash);
        if(!BCrypt.checkpw(password,hash)||statement.contains(password)||statement.contains("O'Marathe")||!statement.endsWith(",'ADMIN');\n"))
            throw new IllegalStateException("Administrator setup self-check failed.");
        try { sql("Manish\nMarathe","manish@example.com",hash); throw new IllegalStateException("Invalid name was accepted."); }
        catch(IllegalArgumentException expected) { }
        System.out.println("Administrator setup self-check passed. No secret file was created.");
    }
    public static void main(String[] args) throws Exception {
        if(args.length==1&&args[0].equals("--self-test")){selfTest();return;}
        if(args.length!=1)throw new IllegalArgumentException("Pass the private output file path.");
        Console console=System.console();
        if(console==null)throw new IllegalStateException("Run this helper in an interactive PowerShell terminal.");
        String name=console.readLine("Administrator name [Manish Marathe]: ");
        if(name==null||name.isBlank())name="Manish Marathe";
        String email=console.readLine("Real administrator email: ");
        if(email!=null&&(email.toLowerCase(Locale.ROOT).endsWith(".local")||email.toLowerCase(Locale.ROOT).endsWith(".invalid")))
            throw new IllegalArgumentException("Use an email that can receive password-reset messages.");
        char[] password=console.readPassword("Production login password (12+ characters, max 72 UTF-8 bytes): ");
        char[] confirmation=null;
        try {
            if(password==null)throw new IllegalArgumentException("Password entry cancelled.");
            confirmation=console.readPassword("Confirm password: ");
            String value=new String(password);
            if(value.isBlank()||value.length()<12||value.getBytes(StandardCharsets.UTF_8).length>72||!Arrays.equals(password,confirmation))
                throw new IllegalArgumentException("Passwords must match and meet the length limits.");
            String statement=sql(name,email,BCrypt.hashpw(value,BCrypt.gensalt(12)));
            Files.writeString(Path.of(args[0]),statement,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            System.out.println("Private administrator initialization file created. Password and hash were not printed.");
        } finally {
            if(password!=null)Arrays.fill(password,'\0');
            if(confirmation!=null)Arrays.fill(confirmation,'\0');
        }
    }
}
