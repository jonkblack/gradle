/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugins.cpp.cdt.model

import org.gradle.api.file.FileCollection
import org.gradle.api.file.ConfigurableFileCollection

import org.gradle.plugins.binaries.model.Binary
import org.gradle.plugins.binaries.model.HeaderExportingSourceSet
import org.gradle.plugins.binaries.model.NativeDependencyCapableSourceSet

import org.gradle.plugins.cpp.CppSourceSet

/**
 * Exposes a more logical view of the actual .cproject descriptor file
 */
class CprojectSettings {

    Binary binary
    private final ConfigurableFileCollection includeRoots
    private final ConfigurableFileCollection libs

    CprojectSettings(Binary binary) {
        this.binary = binary
        includeRoots = binary.project.files()
        libs = binary.project.files()

        binary.sourceSets.withType(HeaderExportingSourceSet).all {
            includeRoots.builtBy(it.exportedHeaders) // have to manually add because we use srcDirs in from, not the real collection
            includeRoots.from(it.exportedHeaders.srcDirs)
        }
        
        binary.sourceSets.withType(NativeDependencyCapableSourceSet).all {
            it.nativeDependencySets.all {
                this.includeRoots.from(it.includeRoots)
                this.libs.from(it.files)
            }
        }
        
        binary.sourceSets.withType(CppSourceSet).all { sourceSet ->
            sourceSet.libs.all { lib ->
                this.libs.from(lib.spec.outputFile)
                this.libs.builtBy(lib.spec.task)
                this.includeRoots.from(lib.headers.srcDirs)
            }
        }
    }

    void applyTo(CprojectDescriptor descriptor) {
        if (binary) {
            applyBinaryTo(descriptor)
        } else {
            throw new IllegalStateException("no binary set")
        }
    }

    private applyBinaryTo(CprojectDescriptor descriptor) {
        descriptor.rootCppCompilerTools.each { compiler ->
            def includePathsOption = descriptor.getOrCreateIncludePathsOption(compiler)
            new LinkedList(includePathsOption.children()).each { includePathsOption.remove(it) }
            includeRoots.each { includeRoot ->
                includePathsOption.appendNode("listOptionValue", [builtIn: "false", value: includeRoot.absolutePath])
            }
        }
        
        descriptor.rootCppLinkerTools.each { linker ->
            def libsOption = descriptor.getOrCreateLibsOption(linker)
            new LinkedList(libsOption.children()).each { libsOption.remove(it) }
            libs.each { lib ->
                libsOption.appendNode("listOptionValue", [builtIn: "false", value: lib.absolutePath])
            }
        }
    }
}