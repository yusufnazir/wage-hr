import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class Hash { public static void main(String[] a){System.out.println(new BCryptPasswordEncoder().encode("ChangeMe!1"));}}
