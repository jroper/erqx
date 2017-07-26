package au.id.jazzy.erqx.engine

import akka.actor.ActorSystem
import au.id.jazzy.erqx.engine.controllers.BlogsRouter
import _root_.controllers.{Assets, AssetsFinder}
import play.api.mvc.ControllerComponents
import play.api.{Configuration, Environment}

trait BlogComponents {
  def environment: Environment
  def configuration: Configuration
  def actorSystem:  ActorSystem
  def controllerComponents: ControllerComponents
  def assets: Assets
  def assetsFinder: AssetsFinder

  lazy val blogs: Blogs = new Blogs(environment, configuration, actorSystem)
  lazy val blogsRouter: BlogsRouter = new BlogsRouter(controllerComponents, blogs, assets, assetsFinder)
}
