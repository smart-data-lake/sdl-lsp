package io.smartdatalake.schema

import io.circe.parser.parse
import io.smartdatalake.logging.SDLBLogger
import sttp.client3.*
import sttp.model.Uri

import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.time.Instant
import scala.concurrent.duration.*
import scala.util.Try

/**
  * JSON schemas of the SDLB versions published at [[baseUrl]], cached in a local directory.
  * A cached release is used forever, a cached snapshot is downloaded again once older than [[snapshotMaxAge]].
  * The index of the base URL lists the available schemas, to fall back to the closest lower version of a version without schema.
  *
  * @param fetch body of the given URL, or an error
  */
class SchemaRepository(cacheDir: Path,
                       fetch: String => Either[String, String] = SchemaRepository.httpGet,
                       baseUrl: String = SchemaRepository.DEFAULT_BASE_URL,
                       snapshotMaxAge: FiniteDuration = 1.day) extends SDLBLogger:

  /** The cached schema of exactly this version, if it doesn't need to be downloaded again */
  def cachedSchema(version: SdlbVersion): Option[Path] = cached(version).filter(isFresh(_, version))

  /**
    * The schema of the given version, else of the closest lower version available. Blocking: downloads if needed.
    * @return the version of the schema and its file
    */
  def schemaFor(version: SdlbVersion, download: Boolean): Either[String, (SdlbVersion, Path)] =
    exactSchema(version, download).map(version -> _).left.flatMap { error =>
      closestLowerVersion(version, download) match
        case Some(lower) => exactSchema(lower, download).map(lower -> _)
        case None => Left(error)
    }

  private def exactSchema(version: SdlbVersion, download: Boolean): Either[String, Path] =
    val cachedFile = cached(version)
    if cachedFile.exists(isFresh(_, version)) then Right(cachedFile.get)
    else if !download then cachedFile.toRight(s"${fileName(version)} is not in the cache $cacheDir and schema download is disabled")
    else
      val url = baseUrl + fileName(version)
      debug(s"Downloading $url")
      fetch(url).flatMap(text => parse(text).left.map(e => s"Invalid JSON at $url: ${e.message}").map(_ => text))
        .flatMap(store(version, _)) match
        case Right(file) => Right(file)
        case Left(error) =>
          // An outdated snapshot is better than no schema
          cachedFile.toRight(error)

  /** The highest released version below the given one, among the published schemas, or the cached ones if they can't be listed */
  private def closestLowerVersion(version: SdlbVersion, download: Boolean): Option[SdlbVersion] =
    val published = if download then fetch(baseUrl).flatMap(listedVersions) else Left("download disabled")
    val available = published.left.map(error => debug(s"Cannot list the published schemas: $error")).getOrElse(cachedVersions)
    available.filter(candidate => !candidate.isSnapshot && summon[Ordering[SdlbVersion]].lt(candidate, version)).maxOption

  private def listedVersions(index: String): Either[String, List[SdlbVersion]] =
    parse(index).flatMap(_.as[List[String]]).left.map(e => s"Invalid index of $baseUrl: ${e.getMessage}").map(_.flatMap(versionOf))

  private def cachedVersions: List[SdlbVersion] =
    Try(Files.list(cacheDir)).toOption.map(stream => try stream.toArray.toList.collect { case p: Path => p.getFileName.toString }.flatMap(versionOf) finally stream.close())
      .getOrElse(Nil)

  private def cached(version: SdlbVersion): Option[Path] = Some(cacheDir.resolve(fileName(version))).filter(Files.isRegularFile(_))

  private def isFresh(file: Path, version: SdlbVersion): Boolean =
    !version.isSnapshot || Try(Files.getLastModifiedTime(file).toInstant.isAfter(Instant.now.minusMillis(snapshotMaxAge.toMillis))).getOrElse(false)

  private def store(version: SdlbVersion, text: String): Either[String, Path] = Try {
    Files.createDirectories(cacheDir)
    val file = cacheDir.resolve(fileName(version))
    // Written completely before replacing the cached file, which another server may be reading
    val temporary = Files.createTempFile(cacheDir, fileName(version), ".tmp")
    Files.writeString(temporary, text)
    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  }.toEither.left.map(e => s"Cannot write the schema to $cacheDir: ${e.getMessage}")

  private def fileName(version: SdlbVersion): String = s"sdl-schema-$version.json"

  private def versionOf(fileName: String): Option[SdlbVersion] =
    Option.when(fileName.startsWith("sdl-schema-") && fileName.endsWith(".json"))(fileName.stripPrefix("sdl-schema-").stripSuffix(".json"))
      .flatMap(SdlbVersion.parse)

object SchemaRepository:
  val DEFAULT_BASE_URL = "https://smartdatalake.ch/json-schema-viewer/schemas/"
  val DEFAULT_CACHE_DIR: Path = Paths.get(System.getProperty("user.home"), ".sdl-lsp", "schemas")

  private lazy val backend = HttpClientSyncBackend()

  def httpGet(url: String): Either[String, String] =
    Uri.parse(url).flatMap { uri =>
      Try(basicRequest.get(uri).readTimeout(30.seconds).send(backend)).toEither.left.map(e => s"Cannot download $url: ${e.getMessage}")
        .flatMap(response => response.body.left.map(_ => s"Cannot download $url: HTTP ${response.code}"))
    }
