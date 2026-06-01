package org.galaxio.gatling.utils.jwt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Paths
import java.security.{PrivateKey, PublicKey}

class JwtKeysSpec extends AnyWordSpec with Matchers {

  "JwtKeys" when {

    "loading RSA keys from classpath" should {

      "load a PKCS#8 private key" in {
        val key = JwtKeys.rsaPrivateKeyFromResource("keys/rsa_private_pkcs8.pem")
        key shouldBe a[PrivateKey]
        key.getAlgorithm shouldBe "RSA"
      }

      "load an X.509 public key" in {
        val key = JwtKeys.rsaPublicKeyFromResource("keys/rsa_public.pem")
        key shouldBe a[PublicKey]
        key.getAlgorithm shouldBe "RSA"
      }
    }

    "loading EC keys from classpath" should {

      "load a PKCS#8 private key" in {
        val key = JwtKeys.ecPrivateKeyFromResource("keys/ec_private_pkcs8.pem")
        key shouldBe a[PrivateKey]
        key.getAlgorithm shouldBe "EC"
      }

      "load an X.509 public key" in {
        val key = JwtKeys.ecPublicKeyFromResource("keys/ec_public.pem")
        key shouldBe a[PublicKey]
        key.getAlgorithm shouldBe "EC"
      }
    }

    "given a missing resource" should {

      "throw IllegalArgumentException with the resource path" in {
        val ex = the[IllegalArgumentException] thrownBy {
          JwtKeys.rsaPrivateKeyFromResource("nonexistent/key.pem")
        }
        ex.getMessage should include("nonexistent/key.pem")
      }
    }

    "loading keys from filesystem" should {

      "load RSA private key from file path" in {
        val path = Paths.get(getClass.getClassLoader.getResource("keys/rsa_private_pkcs8.pem").toURI).toString
        val key  = JwtKeys.rsaPrivateKeyFromFile(path)
        key shouldBe a[PrivateKey]
        key.getAlgorithm shouldBe "RSA"
      }

      "load RSA public key from file path" in {
        val path = Paths.get(getClass.getClassLoader.getResource("keys/rsa_public.pem").toURI).toString
        val key  = JwtKeys.rsaPublicKeyFromFile(path)
        key shouldBe a[PublicKey]
        key.getAlgorithm shouldBe "RSA"
      }

      "load EC private key from file path" in {
        val path = Paths.get(getClass.getClassLoader.getResource("keys/ec_private_pkcs8.pem").toURI).toString
        val key  = JwtKeys.ecPrivateKeyFromFile(path)
        key shouldBe a[PrivateKey]
        key.getAlgorithm shouldBe "EC"
      }

      "load EC public key from file path" in {
        val path = Paths.get(getClass.getClassLoader.getResource("keys/ec_public.pem").toURI).toString
        val key  = JwtKeys.ecPublicKeyFromFile(path)
        key shouldBe a[PublicKey]
        key.getAlgorithm shouldBe "EC"
      }
    }
  }
}
