package au.id.jazzy.erqx.engine.models

/**
 * A page
 *
 * @param permalink The permalink of the page
 * @param format The format of the page
 * @param path The path where the page can be found
 * @param title The title of the page, if defined
 */
case class Page(permalink: String, format: String, path: String, title: Option[String], properties: Yaml = Yaml.empty)
