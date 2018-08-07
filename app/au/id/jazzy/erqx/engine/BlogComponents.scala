package au.id.jazzy.erqx.engine

import akka.actor.ActorSystem
import au.id.jazzy.erqx.engine.controllers.BlogsRouter
import _root_.controllers.{Assets, AssetsFinder}
import au.id.jazzy.erqx.engine.models.ErqxConfig
import play.api.mvc.ControllerComponents
import play.api.Environment
import play.filters.gzip.GzipFilterComponents

trait BlogComponents extends GzipFilterComponents {
  def environment: Environment
  def actorSystem:  ActorSystem
  def controllerComponents: ControllerComponents
  def assets: Assets
  def assetsFinder: AssetsFinder

  lazy val erqxConfig: ErqxConfig = ErqxConfig.fromConfig(configuration)
  lazy val blogs: Blogs = new Blogs(environment, erqxConfig, actorSystem, controllerComponents.messagesApi,
    gzipFilterConfig)(materializer)
  lazy val blogsRouter: BlogsRouter = new BlogsRouter(controllerComponents, blogs, assets, assetsFinder, erqxConfig)
}
