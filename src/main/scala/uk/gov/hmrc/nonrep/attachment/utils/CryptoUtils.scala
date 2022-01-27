package uk.gov.hmrc.nonrep.attachment.utils

import java.security.MessageDigest

object CryptoUtils {
  def calculateSha256(input: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(input).map("%02x".format(_)).mkString
}
