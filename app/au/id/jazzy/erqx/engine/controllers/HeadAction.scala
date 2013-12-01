package au.id.jazzy.erqx.engine.controllers

import play.api.mvc._
import play.api.libs.iteratee._
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Action for handling HEAD requests made to GET actions
 */
class HeadAction(wrapped: EssentialAction) extends EssentialAction with RequestTaggingHandler {

  def apply(req: RequestHeader) = {
    // Invoke the wrapped action
    wrapped(req).map { result =>
      // Tell the body enumerator it's done so that it can clean up resources
      result.body(Done(()))
      result.copy(body = Enumerator.empty)
    }
  }

  // Ensure that request tags are added if necessary
  def tagRequest(request: RequestHeader) = wrapped match {
    case tagging: RequestTaggingHandler => tagging.tagRequest(request)
    case _ => request
  }
}