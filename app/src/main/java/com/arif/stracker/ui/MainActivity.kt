package com.arif.stracker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ResultReceiver
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CalendarView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arif.stracker.R
import com.arif.stracker.service.MotionService
import com.arif.stracker.ui.cards.MotionActivityTextItem
import com.arif.stracker.ui.cards.MotionStatisticsTextItem
import com.arif.stracker.ui.cards.MotionTextItem
import com.arif.stracker.util.Database
import com.arif.stracker.util.Util
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Calendar
import java.util.Locale
import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter


/**
 * The main activity for the UI of the step counter.
 */
internal class MainActivity : AppCompatActivity() {
    private lateinit var mTextViewSteps: TextView
    private lateinit var mTextViewMeters: TextView
    private lateinit var mTextViewCalendarContent: TextView
    private lateinit var mCalendarView: CalendarView
    private lateinit var mChart: Chart
    private lateinit var mTextViewChart: TextView
    private lateinit var mTextViewLatitude: TextView
    private lateinit var mTextViewLongitude: TextView
    private lateinit var btOpenMap: Button
    private var mAdapter: TextItemAdapter = TextItemAdapter()
    private var mCurrentSteps: Int = 0
    private var mSelectedWeek: Int = 0


    private val LOCATION_PERMISSION_REQ_CODE = 1000
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0

    data class parData(
        val lati: Double,
        val longi: Double,
        val curStep: Int
    )
    val pData = mutableListOf<parData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mTextViewSteps = findViewById(R.id.textViewSteps)
        mTextViewMeters = findViewById(R.id.textViewMeters)
        mTextViewLatitude = findViewById(R.id.tvLatitude)
        mTextViewLongitude = findViewById(R.id.tvLongitude)
        btOpenMap = findViewById(R.id.btOpenMap)
        val mRecyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        mTextViewCalendarContent = findViewById(R.id.textViewCalendarContent)
        mChart = findViewById(R.id.chart)
        mTextViewChart = findViewById(R.id.textViewChart)
        mCalendarView = findViewById(R.id.calendar)
        mCalendarView.minDate = Database.getInstance(this).firstEntry
        mCalendarView.maxDate = Util.calendar.timeInMillis
        mCalendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = Util.calendar
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            setDataForWeek(cal)
        }

        // initialize fused location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mLayoutManager = LinearLayoutManager(this)
        mRecyclerView.layoutManager = mLayoutManager
        mRecyclerView.adapter = mAdapter

        // setup swipeable cards and remove them on swiped, used for activities
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView,
                                viewHolder: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val item = mAdapter[viewHolder.adapterPosition]
                if (item is MotionActivityTextItem) {
                    val i = Intent(this@MainActivity, MotionService::class.java)
                    i.action = MotionService.ACTION_STOP_ACTIVITY
                    i.putExtra(MotionService.KEY_ID, item.id)
                    startService(i)
                    mAdapter.remove(viewHolder.adapterPosition)
                }
            }

            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val swipeFlags = ItemTouchHelper.START or ItemTouchHelper.END
                return if (mAdapter[viewHolder.adapterPosition].isSwipeable) {
                    ItemTouchHelper.Callback.makeMovementFlags(0, swipeFlags)
                } else 0
            }

        }).attachToRecyclerView(mRecyclerView)

        findViewById<View>(R.id.btGetLocation).setOnClickListener {
            getCurrentLocation()
            pData.add(parData(latitude, longitude, mCurrentSteps))
            //FileOutputStream("filename.csv").apply { writeCsv(pData)}
        }

        btOpenMap.setOnClickListener {
            openMap()
        }


        // initial update of the diagram
        setDataForWeek(Util.calendar)

        // Add some cards with statistics
        setupCards()

        // Start the motion service
        checkPermission()
    }

    private fun getCurrentLocation() {
        // checking location permission
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // request permission
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQ_CODE)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                // getting the last known or current location
                latitude = location.latitude
                longitude = location.longitude
                mTextViewLatitude.text = "Latitude: ${location.latitude}"
                mTextViewLongitude.text = "Longitude: ${location.longitude}"

                btOpenMap.visibility = View.VISIBLE
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed on getting current location",
                    Toast.LENGTH_SHORT).show()
            }
    }

    private fun openMap() {
        val uri = Uri.parse("geo:${latitude},${longitude}")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
        mapIntent.setPackage("com.google.android.apps.maps")
        startActivity(mapIntent)
    }

    fun OutputStream.writeCsv(pData: List<parData>) {
        val writer = bufferedWriter()
        writer.write(""""Latitude", "Longitude", "Current Step"""")
        writer.newLine()
        pData.forEach {
            writer.write("${it.lati}, ${it.longi}, \"${it.curStep}\"")
            writer.newLine()
        }
        writer.flush()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        AppCompatDelegate.setDefaultNightMode(
            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else AppCompatDelegate.MODE_NIGHT_YES)
        return super.onOptionsItemSelected(item)
    }

    private fun setupCards() {
        val startOfMonth = Calendar.getInstance()
        startOfMonth.set(Calendar.DAY_OF_MONTH, 1)

        // A card that displays sum of steps in the current month
        val stepsThisMonth = Database.getInstance(this).getSumSteps(startOfMonth.timeInMillis)
        mAdapter.add(MotionStatisticsTextItem(this, R.string.steps_month, stepsThisMonth))

        // A card that displays average distance in a day
        val item = MotionTextItem(this, R.string.avg_distance)
        item.setContent(Database.getInstance(this).avgSteps)
        mAdapter.add(item)

        // A card that displays overall sum of steps
        val overallSteps = Database.getInstance(this).getSumSteps(0)
        mAdapter.add(MotionStatisticsTextItem(this, R.string.overall_distance, overallSteps))
    }

    private fun subscribeService() {
        // start the service and pass a result receiver that is used by the service to update the UI
        val i = Intent(this, MotionService::class.java)
        i.action = MotionService.ACTION_SUBSCRIBE
        i.putExtra(RECEIVER_TAG, object : ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
                if (resultCode == 0) {
                    runOnUiThread { updateView(resultData.getInt(MotionService.KEY_STEPS), resultData.getParcelableArrayList(MotionService.KEY_ACTIVITIES) ?: ArrayList()) }
                }
            }
        })
        startService(i)
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 0)
        } else {
            subscribeService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQ_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission granted
                } else {
                    // permission denied
                    Toast.makeText(this, "You need to grant permission to access location",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
        subscribeService()
    }

    private fun updateView(steps: Int, activities: MutableList<Bundle>) {
        // update current today's steps in the header
        mCurrentSteps = steps
        mTextViewMeters.text = String.format(getString(R.string.meters_today), Util.stepsToMeters(steps))
        mTextViewSteps.text = resources.getQuantityString(R.plurals.steps_text, steps, steps)

        // update calendar max date for the case that new day started
        mCalendarView.maxDate = Util.calendar.timeInMillis

        // update the cards
        for (i in 0 until mAdapter.itemCount) {
            val item = mAdapter[i]
            if (item is MotionStatisticsTextItem) {
                item.updateSteps(steps)
            } else if (item is MotionActivityTextItem) {
                for (activity in activities) {
                    if (activity.getInt(MotionService.KEY_ID) == item.id) {
                        item.updateSteps(activity.getInt(MotionService.KEY_STEPS))
                        activities.remove(activity)
                        break
                    }
                }
            }
        }

        // initialize dynamic cards, e.g. activities, that are not yet added
        for (activity in activities) {
            val id = activity.getInt(MotionService.KEY_ID)
            val item = MotionActivityTextItem(this, id, View.OnClickListener {
                val i = Intent(this@MainActivity, MotionService::class.java)
                i.action = MotionService.ACTION_TOGGLE_ACTIVITY
                i.putExtra(MotionService.KEY_ID, id)
                startService(i)
            })
            item.updateSteps(activity.getInt(MotionService.KEY_STEPS))
            item.setActive(activity.getBoolean(MotionService.KEY_ACTIVE))
            mAdapter.addTop(item)
        }

        // If selected week is the current week, update the diagram with today's steps
        if (mSelectedWeek == Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)) {
            mChart.setCurrentSteps(steps)
            mChart.update()
        }
    }

    private fun setDataForWeek(selected: Calendar) {
        mSelectedWeek = selected.get(Calendar.WEEK_OF_YEAR)

        val min = Calendar.getInstance()
        min.timeInMillis = selected.timeInMillis

        // Jump to the first day of the week
        min.set(Calendar.DAY_OF_WEEK, Calendar.getInstance().firstDayOfWeek)

        val max = Calendar.getInstance()
        max.timeInMillis = min.timeInMillis

        // Jump to the last day of the week
        max.add(Calendar.DAY_OF_YEAR, 6)

        mChart.clearDiagram()
        mTextViewChart.text = String.format(Locale.getDefault(), getString(R.string.week_display_format), min.get(Calendar.WEEK_OF_YEAR), min.timeInMillis, max.timeInMillis)

        // Get the records of the selected week between the min and max timestamps
        val entries = Database.getInstance(this).getEntries(min.timeInMillis, max.timeInMillis)

        mTextViewCalendarContent.text = String.format(getString(R.string.no_entry), selected.timeInMillis)
        for (entry in entries) {
            mChart.setDiagramEntry(entry)

            val cal = Calendar.getInstance()
            cal.timeInMillis = entry.timestamp

            // Update the description text with the selected date
            if (cal.get(Calendar.DAY_OF_WEEK) == selected.get(Calendar.DAY_OF_WEEK)) {
                mTextViewCalendarContent.text = String.format(Locale.getDefault(), getString(R.string.steps_day_display), cal.timeInMillis, Util.stepsToMeters(entry.steps), entry.steps)
            }
        }

        // If selected week is the current week, update the diagram with today's steps
        if (mSelectedWeek == Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)) {
            mChart.setCurrentSteps(mCurrentSteps)
        }
        mChart.update()
    }

    companion object {

        const val RECEIVER_TAG = "RECEIVER_TAG"
    }
}
