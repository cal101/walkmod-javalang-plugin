/* 
  Copyright (C) 2013 Raquel Pau and Albert Coroleu.
 
 Walkmod is free software: you can redistribute it and/or modify
 it under the terms of the GNU Lesser General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.
 
 Walkmod is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Lesser General Public License for more details.
 
 You should have received a copy of the GNU Lesser General Public License
 along with Walkmod.  If not, see <http://www.gnu.org/licenses/>.*/

package org.walkmod.javalang.walkers;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.walkmod.exceptions.WalkModException;
import org.walkmod.javalang.ASTManager;
import org.walkmod.javalang.ast.CompilationUnit;
import org.walkmod.javalang.ast.body.ModifierSet;
import org.walkmod.javalang.ast.body.TypeDeclaration;
import org.walkmod.javalang.ast.expr.NameExpr;
import org.walkmod.javalang.util.FileUtils;
import org.walkmod.walkers.AbstractWalker;
import org.walkmod.walkers.ChangeLogPrinter;
import org.walkmod.walkers.VisitorContext;

public class DefaultJavaWalker extends AbstractWalker {

	private File originalFile;

	public static final String ORIGINAL_FILE_KEY = "original_file_key";

	private static Logger log = Logger.getLogger(DefaultJavaWalker.class);

	private boolean reportChanges = true;

	private boolean onlyWriteChanges = true;

	private Map<String, Integer> added = new HashMap<String, Integer>();

	private Map<String, Integer> deleted = new HashMap<String, Integer>();

	private Map<String, Integer> updated = new HashMap<String, Integer>();

	private Map<String, Integer> unmodified = new HashMap<String, Integer>();

	private String encoding = "UTF-8";

	public void accept(File file) throws Exception {
		originalFile = file;
		visit(file);
	}

	public void visit(File file) throws Exception {
		if (file.getName().endsWith(".java")) {
			CompilationUnit cu = null;
			try {
				cu = ASTManager.parse(file, encoding);
			} catch (Exception e) {
				throw new WalkModException("The file " + file.getPath()
						+ " has an invalid java format", e);
			}
			if (getRootNamespace() != null && !"".equals(getRootNamespace())) {
				String qualifiedName = getResource().getNearestNamespace(file,
						NAMESPACE_SEPARATOR);
				if (getRootNamespace().startsWith(qualifiedName)) {
					return;
				}
			}
			if (cu != null) {
				log.debug(file.getPath() + " [ visiting ]");
				visit(cu);
				log.debug(file.getPath() + " [ visited ]");
			} else {
				log.warn("Empty compilation unit");
			}
		}
	}

	private void mapSum(Map<String, Integer> op1, Map<String, Integer> op2) {
		Set<String> keys = op2.keySet();
		for (String key : keys) {
			Integer aux = op1.get(key);
			if (aux == null) {
				op1.put(key, op2.get(key));
			} else {
				op1.put(key, op2.get(key) + aux);
			}
		}
	}

	protected void write(Object element) throws Exception {
		if (element != null && element instanceof CompilationUnit) {
			VisitorContext vc = new VisitorContext(getChainConfig());
			vc.put(ORIGINAL_FILE_KEY, originalFile);
			if (reportChanges) {
				CompilationUnit returningCU = (CompilationUnit) element;
				CompilationUnit cu = null;
				try {
					cu = ASTManager.parse(originalFile, encoding);
				} catch (Exception e) {
					throw new WalkModException("Exception writing results of "
							+ originalFile.getPath(), e);
				}
				if (cu != null) {
					// if the returning CU corresponds to the same File, then
					// check changes.
					boolean samePackage = cu.getPackage() == null
							&& returningCU.getPackage() == null;
					samePackage = samePackage
							|| (cu.getPackage() != null
									&& returningCU.getPackage() != null && cu
									.getPackage().equals(
											returningCU.getPackage()));
					boolean sameType = cu.getTypes() == null
							&& returningCU.getTypes() == null;
					sameType = sameType
							|| (cu.getTypes() != null
									&& returningCU.getTypes() != null && cu
									.getTypes()
									.get(0)
									.getName()
									.equals(returningCU.getTypes().get(0)
											.getName()));
					boolean resolveWrite = false;
					if (samePackage && sameType) {
						ChangeLogVisitor clv = new ChangeLogVisitor();
						VisitorContext ctx = new VisitorContext();
						ctx.put(ChangeLogVisitor.NODE_TO_COMPARE_KEY, cu);
						clv.visit((CompilationUnit) element, ctx);
						if (clv.isUpdated()) {
							log.debug(originalFile.getPath()
									+ " [with changes]");
							String name = "";
							if (cu.getPackage() != null) {
								name = cu.getPackage().getName().toString();
							}
							if (cu.getTypes() != null
									&& !cu.getTypes().isEmpty()) {
								if (cu.getPackage() != null) {
									name = name + ".";
								}
								name = name + cu.getTypes().get(0).getName();
							}
							Map<String, Integer> auxAdded = clv.getAddedNodes();
							Map<String, Integer> auxUpdated = clv
									.getUpdatedNodes();
							Map<String, Integer> auxDeleted = clv
									.getDeletedNodes();
							Map<String, Integer> auxUnmodified = clv
									.getUnmodifiedNodes();
							if (added.isEmpty()) {
								added = auxAdded;
							} else {
								mapSum(added, auxAdded);
							}
							if (updated.isEmpty()) {
								updated = auxUpdated;
							} else {
								mapSum(updated, auxUpdated);
							}
							if (deleted.isEmpty()) {
								deleted = auxDeleted;
							} else {
								mapSum(deleted, auxDeleted);
							}
							if (unmodified.isEmpty()) {
								unmodified = auxUnmodified;
							} else {
								mapSum(unmodified, auxUnmodified);
							}
							log.info(">> " + name);
							ChangeLogPrinter printer = new ChangeLogPrinter(
									auxAdded, auxUpdated, auxDeleted,
									auxUnmodified);
							printer.print();
							write(element, vc);
							log.debug(originalFile.getPath() + " [ written ]");
						} else {
							log.debug(originalFile.getPath()
									+ " [ without changes ] ");
							resolveWrite = true;
						}
					} else {
						resolveWrite = true;
					}
					if (resolveWrite) {
						String name = null;
						if (returningCU.getTypes() != null) {
							name = returningCU.getTypes().get(0).getName();
							if (returningCU.getPackage() != null) {
								String aux = returningCU.getPackage().getName()
										.toString();
								name = aux + "." + name;
							}
							if (name != null) {
								File outputDir = FileUtils.getSourceFile(
										new File(getChainConfig()
												.getWriterConfig().getPath()),
										returningCU.getPackage(), returningCU
												.getTypes().get(0));
								log.debug(outputDir.getPath()
										+ " [ output file ]");
								if (!outputDir.exists()) {
									log.info("++ " + name);
									vc.remove(ORIGINAL_FILE_KEY);
									write(element, vc);
									log.debug(outputDir.getPath()
											+ " [ created ]");
									log.debug(outputDir.getPath()
											+ " [ written ]");
								} else {
									if (!outputDir.equals(originalFile)) {
										vc.put(ORIGINAL_FILE_KEY, outputDir);
										write(element, vc);
										log.debug(outputDir.getPath()
												+ " [ overwritten ]");
									} else if (!onlyWriteChanges) {
										vc.put(ORIGINAL_FILE_KEY, outputDir);
										write(element, vc);
										log.debug(outputDir.getPath()
												+ " [ overwritten ]");
									} else {
										log.debug(originalFile.getPath()
												+ " [not written] ");
									}
								}
							}
						}
					}
				}
			} else {
				log.warn("report changes options is not active");
				write(element, vc);
				log.debug(originalFile.getPath() + " [ written ]");
			}
		}
	}

	@Override
	protected String getLocation(VisitorContext ctx) {
		return ((File) ctx.get(ORIGINAL_FILE_KEY)).getAbsolutePath();
	}

	@Override
	protected void visit(Object element) throws Exception {
		VisitorContext context = new VisitorContext(getChainConfig());
		context.put(ORIGINAL_FILE_KEY, originalFile);
		visit(element, context);
		addVisitorMessages(context);
	}

	@Override
	public int getNumModifications() {
		Integer aux = updated.get(CompilationUnit.class.getSimpleName());
		if (aux == null) {
			return 0;
		}
		return aux;
	}

	@Override
	public int getNumAdditions() {
		Integer aux = added.get(CompilationUnit.class.getSimpleName());
		if (aux == null) {
			return 0;
		}
		return aux;
	}

	@Override
	public int getNumDeletions() {
		Integer aux = deleted.get(CompilationUnit.class.getSimpleName());
		if (aux == null) {
			return 0;
		}
		return aux;
	}

	@Override
	public boolean reportChanges() {
		return reportChanges;
	}

	@Override
	public void setReportChanges(boolean reportChanges) {
		this.reportChanges = reportChanges;
	}

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	@Override
	protected Object getSourceNode(Object targetNode) {
		Object result = null;
		if (targetNode instanceof CompilationUnit) {
			CompilationUnit targetCU = (CompilationUnit) targetNode;
			String path = getChainConfig().getWriterConfig().getPath()
					+ File.separator;
			if (targetCU.getPackage() != null) {
				NameExpr packageName = targetCU.getPackage().getName();
				String packPath = packageName.toString().replace('.',
						File.separatorChar);
				path = path + packPath + File.separator
						+ getPublicTypeDeclaration(targetCU) + ".java";
			} else {
				path = path + getPublicTypeDeclaration(targetCU) + ".java";
			}
			File sourceFile = new File(path);
			if (sourceFile.exists()) {
				try {
					result = ASTManager.parse(sourceFile);
				} catch (Exception e) {
					throw new WalkModException(e);
				}
			}
		}
		return result;
	}

	private String getPublicTypeDeclaration(CompilationUnit cu) {
		for (TypeDeclaration td : cu.getTypes()) {
			if (td.getModifiers() == ModifierSet.PUBLIC) {
				return td.getName();
			}
		}
		throw new WalkModException(
				"Illegal typeDeclaration list, for compilationUnit. No public type found");
		// TODO faltaria poner qu� compilationUnit es. Hacer un assert?
	}
}