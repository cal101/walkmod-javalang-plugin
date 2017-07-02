package org.walkmod.javalang.walkers;

import com.google.common.base.Joiner;
import org.walkmod.javalang.ast.CompilationUnit;
import org.walkmod.javalang.ast.ImportDeclaration;
import org.walkmod.javalang.ast.Node;
import org.walkmod.javalang.ast.expr.MethodCallExpr;
import org.walkmod.javalang.ast.expr.NameExpr;
import org.walkmod.javalang.ast.expr.QualifiedNameExpr;
import org.walkmod.javalang.compiler.symbols.RequiresSemanticAnalysis;
import org.walkmod.javalang.visitors.VoidVisitorAdapter;
import org.walkmod.walkers.VisitorContext;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Execute some AST changes for integration testing.
 */
@RequiresSemanticAnalysis
public class ExampleAstChangeVisitor extends VoidVisitorAdapter<VisitorContext> {
    private static final String OLD_JUNIT_FRAMEWORK_ASSERT = "junit.framework.Assert";
    private static final String NEW_JUNIT_ASSERT = "org.junit.Assert";

    @Override
    public void visit(MethodCallExpr n, VisitorContext arg) {
        super.visit(n, arg);

        if (n.getName().equals("fail")
                && n.getSymbolData().getMethod().getDeclaringClass().getName().equals(OLD_JUNIT_FRAMEWORK_ASSERT)) {
            addImport(n, NEW_JUNIT_ASSERT);
            removeImport(n, OLD_JUNIT_FRAMEWORK_ASSERT, false);
            final MethodCallExpr md = new MethodCallExpr(
                    new NameExpr("Assert"),
                    deepClone(n.getTypeArgs()),
                    n.getName(),
                    deepClone(n.getArgs()));

            n.getParentNode().replaceChildNode(n, md);
        }
    }

    private static void addImport(Node n, final String name) {
        addImport(n, name, false);
    }

    private static void addImport(Node n, String name, boolean isStatic) {
        CompilationUnit cu = findCompilationUnit(n);
        if (cu != null) {
            ImportDeclaration idecl = findImport(cu, name, isStatic);
            if (idecl == null) {
                ImportDeclaration newIDecl = new ImportDeclaration(createQName(name), isStatic, false);
                List<ImportDeclaration> imps = cu.getImports() != null ? cu.getImports() : new ArrayList<ImportDeclaration>();
                imps.add(newIDecl);
                cu.setImports(imps);
            }
        }
    }

    private static ImportDeclaration findImport(CompilationUnit cu, String name, boolean isStatic) {
        ImportDeclaration idecl = null;
        final List<ImportDeclaration> imports = cu.getImports();
        if (imports != null) {
            for (ImportDeclaration anImport : imports) {
                if (name.equals(qname(anImport.getName())) && anImport.isStatic() == isStatic) {
                    idecl = anImport;
                    break;
                }
            }
        }
        return idecl;
    }

    private static void removeImport(Node n, final String name, boolean isStatic) {
        CompilationUnit cu = findCompilationUnit(n);
        if (cu != null) {
            ImportDeclaration idecl = findImport(cu, name, isStatic);
            if (idecl != null) {
                cu.removeChild(idecl);
            }
        }
    }

    private static CompilationUnit findCompilationUnit(Node n) {
        while (n != null && !(n instanceof CompilationUnit)) {
            n = n.getParentNode();
        }
        return n != null ? (CompilationUnit) n : null;
    }

    private static NameExpr createQName(String name) {
        NameExpr ne = null;
        final String[] parts = name.split("\\.");
        for (String part : parts) {
            if (ne == null) {
                ne = new NameExpr(part);
            } else {
                ne = new QualifiedNameExpr(ne, part);
            }
        }
        return ne;
    }

    private static String qname(NameExpr ne) {
        if (ne == null || ne.getName() == null) {
            return null;
        }
        LinkedList<String> parts = new LinkedList<String>();
        while (ne != null && ne.getName() != null) {
            parts.add(0, ne.getName());
            ne = ne instanceof QualifiedNameExpr ? ((QualifiedNameExpr)ne).getQualifier() : null;
        }
        return Joiner.on('.').join(parts);
    }

    private static <T extends Node> List<T> deepClone(List<T> l) {
        if (l != null) {
            List<T> res = new ArrayList<T>(l.size());
            for (T o : l) {
                res.add(clone(o));
            }
            return res;
        } else {
            return null;
        }
    }

    private static <T extends Node> T clone(T it) {
        try {
            return (T) it.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
