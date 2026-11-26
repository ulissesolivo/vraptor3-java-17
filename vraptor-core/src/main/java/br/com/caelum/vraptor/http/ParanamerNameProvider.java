/***
 * Copyright (c) 2009 Caelum - www.caelum.com.br/opensource
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package br.com.caelum.vraptor.http;

import java.lang.reflect.AccessibleObject;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.caelum.vraptor.ioc.ApplicationScoped;
import br.com.caelum.vraptor.util.Stringnifier;

import com.thoughtworks.paranamer.AnnotationParanamer;
import com.thoughtworks.paranamer.BytecodeReadingParanamer;
import com.thoughtworks.paranamer.CachingParanamer;
import com.thoughtworks.paranamer.ParameterNamesNotFoundException;
import com.thoughtworks.paranamer.Paranamer;

/**
 * Paranamer based parameter name provider provides parameter names based on
 * their local variable name during compile time. Information is retrieved using
 * paranamer's mechanism.
 *
 * @author Guilherme Silveira
 */
@ApplicationScoped
public class ParanamerNameProvider implements ParameterNameProvider {
	private final Paranamer info = new CachingParanamer(new AnnotationParanamer(new ReflectionFirstParanamer()));

	private static final Logger logger = LoggerFactory.getLogger(ParanamerNameProvider.class);

	public String[] parameterNamesFor(AccessibleObject method) {
		try {
			String[] parameterNames = info.lookupParameterNames(method);
			if (logger.isDebugEnabled()) {
				logger.debug("Found parameter names with paranamer for {} as {}",
					Stringnifier.simpleNameFor(method), Arrays.toString(parameterNames));
			}

			// maybe paranamer cache already provides defensive copies?
			String[] defensiveCopy = new String[parameterNames.length];
			System.arraycopy(parameterNames, 0, defensiveCopy, 0, parameterNames.length);
			return defensiveCopy;
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new IllegalStateException("Paranamer could not read the class file of " + method
				+ ". It is probably newer than paranamer supports; compile your code with javac -parameters.", e);
		} catch (ParameterNamesNotFoundException e) {
			throw new IllegalStateException("Paranamer were not able to find your parameter names for " + method
					+ "You must compile your code with debug information (javac -g) or register another "
					+ "name provider. Try to use " + DefaultParameterNameProvider.class.getName() + " instead.");
		}
	}

	/**
	 * Uses the names kept by the compiler (javac -parameters) when present, which does not depend on
	 * reading the class bytecode (older paranamer versions fail on recent class file formats). Falls back
	 * to the bytecode reading otherwise. Names from {@code @Named} still take precedence.
	 */
	private static class ReflectionFirstParanamer implements Paranamer {
		private final Paranamer bytecode = new BytecodeReadingParanamer();

		public String[] lookupParameterNames(AccessibleObject methodOrCtor) {
			return lookupParameterNames(methodOrCtor, true);
		}

		public String[] lookupParameterNames(AccessibleObject methodOrCtor, boolean throwExceptionIfMissing) {
			if (methodOrCtor instanceof java.lang.reflect.Executable) {
				java.lang.reflect.Parameter[] parameters = ((java.lang.reflect.Executable) methodOrCtor).getParameters();
				String[] names = new String[parameters.length];
				boolean present = true;
				for (int i = 0; i < parameters.length && present; i++) {
					present = parameters[i].isNamePresent();
					names[i] = parameters[i].getName();
				}
				if (present) {
					return names;
				}
			}
			try {
				return bytecode.lookupParameterNames(methodOrCtor, throwExceptionIfMissing);
			} catch (ArrayIndexOutOfBoundsException e) {
				throw new IllegalStateException("Paranamer could not read the class file of " + methodOrCtor
					+ ". It is probably newer than paranamer supports; compile your code with javac -parameters.", e);
			}
		}
	}

}
