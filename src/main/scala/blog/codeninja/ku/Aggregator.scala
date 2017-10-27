package blog.codeninja.ku

import com.rometools.rome.io._
import org.joda.time.format.PeriodFormatterBuilder
import org.joda.time.Period
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.regex.Pattern
import monix.execution._
import monix.reactive._
import monix.reactive.subjects._
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.Try
import scalaj.http._
import org.slf4j.{Logger, LoggerFactory}

class Aggregator(prefs: Config.Prefs) {
  import Scheduler.Implicits.global

  val logger = LoggerFactory getLogger "Aggregator"

  // parse the maximum age
  val age = {
    val parser = new PeriodFormatterBuilder()
      .appendWeeks().appendSuffix("w")
      .appendDays().appendSuffix("d")
      .appendHours().appendSuffix("h")
      .appendMinutes().appendSuffix("m")
      .appendSeconds().appendSuffix("s")
      .toFormatter()

    prefs.ageLimit flatMap {
      limit => Try(Period.parse(limit, parser)).toOption map (_.toStandardDuration)
    }
  }

  // filter patterns of headlines to hide
  val hideFilters = prefs.filters map {
    s => Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE)
  }

  // create a reactive feed for each url
  val feeds = prefs.urls map {
    _ => PublishSubject[List[Headline]]()
  }

  // pull all the readers together into a single observable
  val allFeeds = Observable.combineLatestList(feeds: _*)

  // all feeds are an flattened togeter into a sorted list of headlines
  val headlines = allFeeds map (_.flatten.sorted.filterNot(isOld _).partition(isHidden _))

  // create a cancelable, periodic reader for all the urls
  val readers = (prefs.urls zip feeds) map ((aggregate _).tupled)

  // stop running the aggregator
  def cancel = readers foreach (_.cancel)

  // true if the age of the headline exceeds the age limit in the preferences
  def isOld(h: Headline): Boolean = age map (h.age.toDuration isLongerThan _) getOrElse false

  // true if this headlnie should be hidden from the user
  def isHidden(h: Headline): Boolean = hideFilters.exists(p => p.matcher(h.title).find)

  // create a scheduled task that reads the given RSS feed
  def aggregate(url: String, feed: PublishSubject[List[Headline]]): Cancelable =
    Observable.intervalAtFixedRate(1.second, 5.minutes)
      .flatMap(_ => Try(readFeed(url)) getOrElse Observable.now(List.empty))
      .foreach(feed onNext _)

  // download the RSS feed, add it to the feed list, and update the view
  def readFeed(url: String, redirects: Int = 5): Observable[List[Headline]] = {
    Http(url).timeout(5000, 10000).asBytes match {
      case r if r.isRedirect && redirects > 0 =>
        readFeed(r.location.get, redirects-1)
      case r if r.isSuccess => {
        val input = new ByteArrayInputStream(r.body)
        val feed = new SyndFeedInput().build(new XmlReader(input))
        val entries = feed.getEntries.asScala map (new Headline(feed, _))

        // output that this feed was parsed
        logger info url

        // publish a new feed map with new headlines
        Observable.now(entries.toList)
      }

      // anything else is an error
      case r => throw new Error(r.statusLine)
    }
  }
}
