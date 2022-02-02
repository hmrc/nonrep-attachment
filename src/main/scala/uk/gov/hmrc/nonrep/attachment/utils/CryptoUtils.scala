package uk.gov.hmrc.nonrep.attachment.utils

import java.security.MessageDigest
import java.util.Base64

import org.apache.commons.codec.digest.DigestUtils

object CryptoUtils {
  def calculateSha256(input: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(input).map("%02x".format(_)).mkString
    
  def calculateMD5(input: Array[Byte]): String = Base64.getEncoder.encodeToString(DigestUtils.md5(input))
}
