package com.hm.iou.appconfig.plugin

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.dsl.LintOptions
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

/**
 * 编译配置
 */
class CompileConfigUtil {

    static def MIN_SDK = 19
    static def TARGET_SDK = 26
    static def COMPILE_SDK = "android-26"

    static void checkCompileConfig(Project project) {
        //如果有 aar 依赖包，定义所有的 aar 包都放在 libs 目录下
        project.repositories {
            flatDir {
                dirs "libs"
            }
        }

        //收集 "implementation" configuration 里的依赖
        def dependencyMap = new HashMap()
        Configuration implementationConf = project.configurations.getByName("implementation")
        implementationConf.getAllDependencies().all { Dependency dependency ->
            dependencyMap.put("${dependency.group}:${dependency.name}:${dependency.version}", dependency)
        }

        project.afterEvaluate {
            com.android.build.gradle.BaseExtension android = project.extensions.getByName("android")

            //强制统一 compileSdkVersion、 minSdkVersion、targetSdkVersion
            String compileSdkVersion = android.compileSdkVersion
            int targetSdkVersion = android.defaultConfig.targetSdkVersion.apiLevel
            int minSdkVersion = android.defaultConfig.minSdkVersion.apiLevel
            if (compileSdkVersion != COMPILE_SDK) {
                throw new GradleException("请修改 compileSdkVersion，必须设置为 ${COMPILE_SDK}")
            }
            if (minSdkVersion != MIN_SDK) {
                throw new GradleException("请修改 minSdkVersion，必须设置为 ${MIN_SDK}")
            }
            if (targetSdkVersion != TARGET_SDK) {
                throw new GradleException("请修改 targetSdkVersion，必须设置为 ${TARGET_SDK}")
            }

            //使用kotlin 之后，会在 META-INF 下面产生很多 kotlin_module 文件
            android.getPackagingOptions().exclude("META-INF/*.kotlin_module")

            //禁止关闭 lint error 检测
            LintOptions lintOptions = android.getLintOptions()
            lintOptions.abortOnError = true

            //将 implementation 里的依赖全部在 api 里加进去
            if (android instanceof LibraryExtension) {
                Configuration apiConf = project.configurations.getByName("api")
                apiConf.setCanBeResolved(true)
                for (Dependency dependency : dependencyMap.values()) {
                    if (dependency instanceof ExternalModuleDependency) {
                        apiConf.dependencies.add(dependency.copy())
                    }
                }
                apiConf.resolve()
            }


            /*
            List dependencyList = new ArrayList()
            apiConf.incoming.resolutionResult.root.dependencies.each { DependencyResult dr ->
                collectDependencyInfo(dr, null, dependencyList)
            }
            */

            //TODO 检查第三方库，防止第三方库的滥用，如果出现这种情况直接终止
            /*
            dependencyList.each { DependencyInfo dependencyInfo ->
                println(dependencyInfo)
            }
            */
        }
    }

    /**
     * 递归收集依赖信息
     *
     * @param dr
     * @param info
     */
    private static void collectDependencyInfo(DependencyResult dr, DependencyInfo info, List dependencyList) {
        DependencyInfo dependInfo = new DependencyInfo(dr.requested.displayName)
        if (info == null) {
            dependencyList.add(dependInfo)
        } else {
            info.addSubDependInfo(dependInfo)
            dependInfo.setLevel(info.getLevel() + 1)
        }
        if (dr instanceof ResolvedDependencyResult) {
            ((ResolvedDependencyResult) dr).selected.dependencies.each { DependencyResult subDr ->
                collectDependencyInfo(subDr, dependInfo, dependencyList)
            }
        }
    }

}