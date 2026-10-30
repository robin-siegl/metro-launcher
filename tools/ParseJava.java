import com.sun.source.util.JavacTask;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Syntax verification only. Android type checking is done by the real Android build. */
public final class ParseJava {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, null);
             var files = Files.walk(Path.of(args.length == 0 ? "app/src" : args[0]))) {
            List<Path> paths = files.filter(p -> p.toString().endsWith(".java")).toList();
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null, manager.getJavaFileObjectsFromPaths(paths));
            task.parse();
            for (var diagnostic : diagnostics.getDiagnostics()) if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                System.err.println(diagnostic); throw new IllegalStateException("Java syntax error");
            }
            System.out.println("PASS: parsed " + paths.size() + " Java source files. Android type checking still requires the SDK.");
        }
    }
}
