package com.austindroids.austinfeedsme.data.eventbrite

import com.austindroids.austinfeedsme.common.utils.EventbriteUtils
import com.austindroids.austinfeedsme.data.Event
import com.austindroids.austinfeedsme.data.EventsDataSource
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor




/**
 * Created by darrankelinske on 8/26/16.
 */
class EventbriteDataSource : EventsDataSource {

    private  val eventsReference = FirebaseDatabase.getInstance().getReference("events")

    override fun getEvents(callback: EventsDataSource.LoadEventsCallback) {

        val rxAdapter = RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io())

        val eventbriteRetrofit = Retrofit.Builder()
                .baseUrl("https://www.eventbriteapi.com")
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(rxAdapter)
                .build()

        val eventbriteService = eventbriteRetrofit.create(EventbriteService::class.java)

        val searchList = arrayOf("taco", "pizza", "beer", "breakfast", "lunch", "dinner", "drinks", "spaghetti", "hamburger")

        val observableList = ArrayList<Observable<EventbriteEvents>>()

        for (searchTerm in searchList) {
            observableList.add(eventbriteService.getEventsByKeyword(searchTerm))
        }

        val eventbriteEventsSubscriber = object : Observer<EventbriteEvents> {

            override fun onSubscribe(d: Disposable) {

            }

            override fun onComplete() {

            }

            override fun onError(e: Throwable) {
                // cast to retrofit.HttpException to get the response code
                if (e is HttpException) {
                    val code = e.code()
                }

            }

            override fun onNext(eventbriteEvents: EventbriteEvents) {

                val convertedEventbriteEvents = ArrayList<Event>()
                for (eventbriteEvent in eventbriteEvents.getEvents()) {
                    convertedEventbriteEvents.add(EventbriteUtils.transformEventBrite(eventbriteEvent))
                }

                cleanAndLoadEventbriteEvents(convertedEventbriteEvents, object : CleanCallback {
                    override fun loadCleanEvents(events: List<Event>) {
                        callback.onEventsLoaded(events)
                    }
                })
            }
        }

        Observable.merge(observableList)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(eventbriteEventsSubscriber)
    }

    override fun saveEvent(eventToSave: Event, callback: EventsDataSource.SaveEventCallback) {

    }

    private fun cleanAndLoadEventbriteEvents(events: MutableList<Event>, callback: CleanCallback) {
        val callbackTimestamp = Date().time
        Timber.d("onResponse: Event's from eventbrite $callbackTimestamp:${events.size}")

        val eventbriteEventMap = HashMap<String, Event>()
        for (event in events) {
            eventbriteEventMap[event.id] = event
        }

        eventsReference.orderByChild("time").startAt(Date().time.toDouble()).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (postSnapshot in dataSnapshot.children) {
                    val event = postSnapshot.getValue(Event::class.java)

                    eventbriteEventMap.remove(event?.id)
                }

                Timber.d("onResponse: Event's from after cleaning $callbackTimestamp:${eventbriteEventMap.size}")
                callback.loadCleanEvents(ArrayList(eventbriteEventMap.values))

            }

            override fun onCancelled(databaseError: DatabaseError) {

            }
        })

    }

    internal interface CleanCallback {
        fun loadCleanEvents(events: List<Event>)
    }
}
