#!/usr/bin/env python3
"""Generates versions/<group>/ gradle projects and .github/workflows/build.yml from one table."""
import os, shutil, json

YARN_BUILD = """plugins {
    id 'net.fabricmc.fabric-loom-remap' version '1.17-SNAPSHOT'
}

version = "${project.mod_version}+mc${project.mc_label}"
group = 'dev.livinginv'
base { archivesName = 'living-inventory' }

project.src_dirs.split(',').each { sourceSets.main.java.srcDir(it.trim()) }
project.res_dirs.split(',').each { sourceSets.main.resources.srcDir(it.trim()) }

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
}

processResources {
    inputs.property "version", project.version
    inputs.property "mc_depends", project.mc_depends
    filesMatching("fabric.mod.json") {
        expand "version": project.version, "mc_depends": project.mc_depends, "java_version": project.java_version
    }
}

tasks.withType(JavaCompile).configureEach {
    it.options.release = Integer.parseInt(project.java_version)
    it.options.encoding = 'UTF-8'
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion(project.java_version)
    targetCompatibility = JavaVersion.toVersion(project.java_version)
}
"""
MOJMAP_BUILD = YARN_BUILD.replace("net.fabricmc.fabric-loom-remap", "net.fabricmc.fabric-loom") \
    .replace('    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"\n', '') \
    .replace('modImplementation', 'implementation')
SETTINGS = """pluginManagement {
    repositories {
        maven { url = 'https://maven.fabricmc.net/' }
        mavenCentral()
        gradlePluginPortal()
    }
}
"""

def yarn(dir, mc, yarn_v, api, java, depends, era, checks, loader="0.16.14", shared="shared/yarn-dc", src=None):
    return dict(kind="yarn", dir=dir, mc=mc, yarn=yarn_v, api=api, java=java, depends=depends, loader=loader,
                src=src or [shared, era], checks=checks)

def mojmap(dir, mc, api, depends, era, checks):
    return dict(kind="mojmap", dir=dir, mc=mc, yarn=None, api=api, java=25, depends=depends, loader="0.19.5",
                src=["shared/mojmap-ext", era], checks=checks)

GROUPS = [
    mojmap("26.3", "26.3", "0.163.0+26.3", ">=26.3 <26.4", "eras/ext3", []),
    mojmap("26.1-26.2", "26.2", "0.161.0+26.2", ">=26.1 <26.3", "eras/ext1", [("26.1.2", None, "0.155.3+26.1.2")]),
    yarn("1.21.6-1.21.11", "1.21.11", "1.21.11+build.6", "0.141.6+1.21.11", 21, ">=1.21.6 <=1.21.11", "eras/pipe",
         [("1.21.6", "1.21.6+build.1", "0.128.2+1.21.6"), ("1.21.7", "1.21.7+build.8", "0.129.0+1.21.7"),
          ("1.21.8", "1.21.8+build.1", "0.136.1+1.21.8"), ("1.21.9", "1.21.9+build.1", "0.134.1+1.21.9"),
          ("1.21.10", "1.21.10+build.3", "0.138.4+1.21.10")], loader="0.19.5"),
    yarn("1.21.5", "1.21.5", "1.21.5+build.1", "0.128.2+1.21.5", 21, ">=1.21.5 <=1.21.5", "eras/layer5", []),
    yarn("1.21.2-1.21.4", "1.21.4", "1.21.4+build.8", "0.119.4+1.21.4", 21, ">=1.21.2 <=1.21.4", "eras/layer",
         [("1.21.2", "1.21.2+build.1", "0.106.1+1.21.2"), ("1.21.3", "1.21.3+build.2", "0.114.1+1.21.3")]),
    yarn("1.20.5-1.21.1", "1.21.1", "1.21.1+build.3", "0.116.17+1.21.1", 21, ">=1.20.5 <=1.21.1", "eras/dc2",
         [("1.20.6", "1.20.6+build.3", "0.100.8+1.20.6"), ("1.21", "1.21+build.9", "0.102.0+1.21")],
         src=["eras/dc2"]),
    yarn("1.19.4", "1.19.4", "1.19.4+build.2", "0.87.2+1.19.4", 17, ">=1.19.4 <=1.19.4", None, [],
         src=["shared/legacy", "eras/old-d"]),
    yarn("1.19.3", "1.19.3", "1.19.3+build.5", "0.76.1+1.19.3", 17, ">=1.19.3 <=1.19.3", None, [],
         src=["shared/legacy", "shared/legacy-inst", "eras/old-c"]),
    yarn("1.19-1.19.2", "1.19.2", "1.19.2+build.28", "0.77.0+1.19.2", 17, ">=1.19 <=1.19.2", None,
         [("1.19", "1.19+build.4", "0.58.0+1.19"), ("1.19.1", "1.19.1+build.6", "0.58.5+1.19.1")],
         src=["shared/legacy", "shared/legacy-inst", "eras/old-b"]),
    yarn("1.17-1.18.2", "1.18.2", "1.18.2+build.4", "0.77.0+1.18.2", 16, ">=1.17 <=1.18.2", None,
         [("1.17.1", "1.17.1+build.65", "0.46.1+1.17"), ("1.18.1", "1.18.1+build.22", "0.46.6+1.18")],
         src=["shared/legacy", "shared/legacy-inst", "eras/old-a"]),
    yarn("1.16.5", "1.16.5", "1.16.5+build.10", "0.42.0+1.16", 8, ">=1.16.5 <=1.16.5", None, [],
         loader="0.14.25", src=["eras/old-e"]),
    yarn("1.20-1.20.4", "1.20.4", "1.20.4+build.3", "0.97.3+1.20.4", 17, ">=1.20 <=1.20.4", "eras/dc",
         [("1.20.1", "1.20.1+build.10", "0.92.12+1.20.1"), ("1.20.2", "1.20.2+build.4", "0.91.6+1.20.2")]),
]

def main(root):
    vdir = os.path.join(root, "versions")
    shutil.rmtree(vdir, ignore_errors=True)
    matrix = []
    for g in GROUPS:
        d = os.path.join(vdir, g["dir"]); os.makedirs(d)
        open(os.path.join(d, "settings.gradle"), "w").write(SETTINGS)
        open(os.path.join(d, "build.gradle"), "w").write(YARN_BUILD if g["kind"] == "yarn" else MOJMAP_BUILD)
        src = ",".join("../../%s/java" % s for s in ["common"] + g["src"])
        res = ",".join("../../%s/resources" % s for s in ["common"] + g["src"] if os.path.isdir(os.path.join(root, s, "resources")))
        props = ["org.gradle.jvmargs=-Xmx3G", "minecraft_version=" + g["mc"]]
        if g["yarn"]: props.append("yarn_mappings=" + g["yarn"])
        props += ["loader_version=" + g["loader"], "fabric_version=" + g["api"], "mod_version=1.0.0",
                  "mc_label=" + g["dir"], "mc_depends=" + g["depends"], "java_version=%d" % g["java"],
                  "src_dirs=" + src, "res_dirs=" + res]
        open(os.path.join(d, "gradle.properties"), "w").write("\n".join(props) + "\n")
        jdk, gradle = (25, "9.6.0") if g["kind"] == "mojmap" else (21, "9.5.1")
        matrix.append(dict(name="build " + g["dir"], dir=g["dir"], java=jdk, gradle=gradle, args="", upload="true"))
        for mc, y, api in g["checks"]:
            args = "-Pminecraft_version=%s -Pfabric_version=%s" % (mc, api)
            if y: args += " -Pyarn_mappings=" + y
            matrix.append(dict(name="check %s @ %s" % (g["dir"], mc), dir=g["dir"], java=jdk, gradle=gradle, args=args, upload="false"))
    # workflow
    lines = []
    for m in matrix:
        lines.append('          - { name: "%s", dir: "%s", java: %d, gradle: "%s", args: "%s", upload: "%s" }'
                     % (m["name"], m["dir"], m["java"], m["gradle"], m["args"], m["upload"]))
    wf = open(os.path.join(root, "tools", "build.yml.tpl")).read().replace("@@MATRIX@@", "\n".join(lines))
    open(os.path.join(root, ".github", "workflows", "build.yml"), "w").write(wf)
    print("groups:", len(GROUPS), "jobs:", len(matrix))

if __name__ == "__main__":
    main(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
