package coursier.clitests

import java.io.File

import utest._

import scala.util.Properties

abstract class LaunchTests extends TestSuite {

  def launcher: String

  val tests = Tests {
    test("fork") {
      val output =
        os.proc(
          launcher,
          "launch",
          "--fork",
          "io.get-coursier:echo:1.0.1",
          "--",
          "foo"
        )
          .call()
          .out.text()
      val expectedOutput = "foo" + System.lineSeparator()
      assert(output == expectedOutput)
    }

    test("non static main class") {
      val res =
        os.proc(
          launcher,
          "launch",
          "--fork",
          "org.scala-lang:scala-compiler:2.13.0",
          "--main-class",
          "scala.tools.nsc.Driver",
          "--property",
          "user.language=en",
          "--property",
          "user.country=US"
        ).call(
          mergeErrIntoOut = true,
          check = false
        )
      assert(res.exitCode != 0)
      val output = res.out.text()
      val expectedInOutput = Seq(
        "Main method",
        "in class scala.tools.nsc.Driver",
        "is not static"
      )
      assert(expectedInOutput.forall(output.contains))
    }

    test("java class path in expansion from launch") {
      import coursier.dependencyString
      val output =
        os.proc(
          launcher,
          "launch",
          "--property",
          s"foo=$${java.class.path}",
          TestUtil.propsDepStr,
          "--",
          "foo"
        )
          .call()
          .out.text()
      val expected = TestUtil.propsCp.mkString(File.pathSeparator) + System.lineSeparator()
      assert(output == expected)
    }

    def inlineApp(): Unit = {
      val output =
        os.proc(
          launcher,
          "launch",
          """{"dependencies": ["io.get-coursier:echo:1.0.1"], "repositories": ["central"]}""",
          "--",
          "foo"
        )
          .call()
          .out.text()
      val expected = "foo" + System.lineSeparator()
      assert(output == expected)
    }
    test("inline app") {
      if (Properties.isWin) "disabled"
      else { inlineApp(); "" }
    }

    def inlineAppWithId(): Unit = {
      val output =
        os.proc(
          launcher,
          "launch",
          """echo:{"dependencies": ["io.get-coursier:echo:1.0.1"], "repositories": ["central"]}""",
          "--",
          "foo"
        )
          .call()
          .out.text()
      val expected = "foo" + System.lineSeparator()
      assert(output == expected)
    }
    test("inline app with id") {
      if (Properties.isWin) "disabled"
      else { inlineAppWithId(); "" }
    }

    test("no vendor and title in manifest") {
      val output =
        os.proc(
          launcher,
          "launch",
          "io.get-coursier:coursier-cli_2.12:2.0.16+69-g69cab05e6",
          "--",
          "launch",
          "io.get-coursier:echo:1.0.1",
          "--",
          "foo"
        )
          .call()
          .out.text()
      val expectedOutput = "foo" + System.lineSeparator()
      assert(output == expectedOutput)
    }

    test("python") {
      val output =
        os.proc(
          launcher,
          "launch",
          "--python",
          "io.get-coursier:scalapy-echo_2.13:1.0.7",
          "--",
          "a",
          "b",
          "foo"
        )
          .call()
          .out.text()
      val expectedOutput = "a b foo" + System.lineSeparator()
      assert(output == expectedOutput)
    }

    test("extra jars") {
      if (Properties.isWin) "Disabled" // issues escaping the parameter ending in '\*'
      else extraJarsTest()
    }
    def extraJarsTest(): Unit = {
      val files = os.proc(launcher, "fetch", "org.scala-lang:scala3-compiler_3:3.1.3")
        .call()
        .out.lines()
        .map(os.Path(_, os.pwd))
      TestUtil.withTempDir { tmpDir0 =>
        val tmpDir = os.Path(tmpDir0, os.pwd)
        val dir    = tmpDir / "cp"
        for (f <- files)
          os.copy.into(f, dir, createFolders = true)
        val output = os.proc(
          launcher,
          "launch",
          "--extra-jars",
          if (Properties.isWin) {
            val q = "\""
            s"$q$dir/*$q"
          }
          else s"$dir/*",
          "-M",
          "dotty.tools.MainGenericCompiler"
        ).call(mergeErrIntoOut = true).out.lines()
        val expectedFirstLines = Seq(
          "Usage: scalac <options> <source files>",
          "where possible standard options include:"
        )
        assert(output.containsSlice(expectedFirstLines))
      }
    }
  }
}
