package com.example;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class MyClass {
	private static final Logger logger = LoggerFactory.getLogger(MyClass.class);

	public static void main(String[] args) throws Exception {
		final CompilationUnit cu = StaticJavaParser.parse(new File("MiClase.java"));

		cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clase -> {
			logger.info("Clase: {}", clase.getNameAsString());

			clase.getMethods().forEach(metodo -> {
				logger.info("  Método: {}", metodo.getNameAsString());
				logger.info("  Tipo retorno: {}", metodo.getType());
				logger.info("  Parámetros: {}", metodo.getParameters());
			});

			clase.getFields().forEach(campo -> logger.info("  Campo: {}", campo));
		});
	}
}