package com.example.app_demo4.fragment

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.app_demo4.R
import com.example.app_demo4.activity.EventReviewActivity
import com.example.app_demo4.model.HomeData
import com.example.app_demo4.model.HomeHolder
import com.firebase.ui.firestore.FirestoreRecyclerAdapter
import com.firebase.ui.firestore.FirestoreRecyclerOptions
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.android.synthetic.main.activity_event_review.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.recyclerview_home_row.*
import kotlinx.android.synthetic.main.recyclerview_home_row.view.*
import java.text.DateFormat
import java.util.*
import kotlin.collections.HashMap


class HomeFragment : Fragment() {

    //Firebase Properties
    private lateinit var mAuth: FirebaseAuth
    private lateinit var mDatabase: FirebaseFirestore
    private lateinit var eventReference: CollectionReference
    private lateinit var memberReference: DocumentReference
    private lateinit var userId: String


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        //set properties
        mDatabase = FirebaseFirestore.getInstance()
        mAuth = FirebaseAuth.getInstance()
        userId = mAuth.currentUser!!.uid

        setUpRecyclerView()

    }


    private fun setUpRecyclerView() {

        val linearLayoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

        eventReference = mDatabase.collection("Events")

        /** # ดึง event ทั้งหมดที่ตรงกับวันนี้ โดยเรียงตามเวลา */

        //get current date
        val cal = Calendar.getInstance()
        cal.get(Calendar.DAY_OF_MONTH)
        cal.get(Calendar.MONTH)
        cal.get(Calendar.YEAR)
        //format date as 'Fab 2, 2021'
        val today = DateFormat.getDateInstance(DateFormat.MEDIUM).format(cal.time)
//        Log.d("TAG", "setUpRecyclerView: $today")


        // Use FirebaseRecyclerAdapter for RecyclerView
        val query = eventReference.whereEqualTo("event_date", today).orderBy("event_time")
        val options = FirestoreRecyclerOptions.Builder<HomeData>()
            .setQuery(query, HomeData::class.java)
            .setLifecycleOwner(this)
            .build()

        val adapter = object : FirestoreRecyclerAdapter<HomeData, HomeHolder>(options) {

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeHolder {
                return HomeHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.recyclerview_home_row, parent, false)
                )
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onBindViewHolder(holder: HomeHolder, position: Int, model: HomeData) {
                holder.bind(model)

                //get eventName
                val eventName = snapshots[position].event_name.toString()
                Log.d("TAG", "onBindViewHolder: $eventName")

                //get event ID
                val eventId = snapshots.getSnapshot(position).id
                Log.d("TAG", "onBindViewHolder: $eventId")


                holder.itemView.apply {

                    val isExpandable: Boolean = snapshots[position].expandable
                    holder.expandableLayout.visibility =
                        if (isExpandable) View.VISIBLE else View.GONE

                    //Button expand
                    this.expand_btn.setOnClickListener {
                        val eventCardList = snapshots[position]
                        eventCardList.expandable = !eventCardList.expandable
                        notifyItemChanged(position)
                    }

                    //Button More Details
                    this.details_btn.setOnClickListener {
//                        val eventId = snapshots.getSnapshot(position).id
                        val intent = Intent(context, EventReviewActivity::class.java)
                        intent.putExtra("eventId", eventId)
                        startActivity(intent)
                    }

                    //Button join
                    this.join_btn.setOnClickListener {

                        //get userName
                        getUserName {
                            createEventMember(it, eventName, eventId)
                        }

                    } //end join btn.

                }
            }
        }


        //set recyclerView layout and adapter
        rv_home.layoutManager = linearLayoutManager
        rv_home.adapter = adapter

    }

    private fun createEventMember(userName: String, eventName: String, eventId: String) {

        Log.d("TAG", "createEventMember: userName $userName")
        Log.d("TAG", "createEventMember: eventName $eventName")
        Log.d("TAG", "createEventMember: eventId $eventId")

        val mId = HashMap<String, Any>().apply {
            this[userId] = userName
        }

        //Create Event-mem-list by event name
        memberReference = mDatabase.collection("Event-mem-list").document(eventName)
        memberReference.apply {
            addSnapshotListener { value, error ->

                if (error != null) {
                    Log.d("TAG", "createEventMember: error ${error.message}")
                }

                value.let {

                    val memId = value?.data?.keys //userId in eventId
                    Log.d("TAG", "createEventMember: memID $memId")

                    //check that memId contains userId (current user) ?
                    if (memId == null || !memId.contains(userId)) {

                        //set data(mId) to Event-mem-list
                        set(mId, SetOptions.mergeFields(userId)).addOnCompleteListener {
                            if (it.isSuccessful) {

                                Snackbar.make(root_layout_home_fm,"You're joined $eventName", Snackbar.LENGTH_LONG)
                                    .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_SLIDE)
                                    .show()
                            }
                        }
                    } else {
                        //Why always show ?
                        Snackbar.make(root_layout_home_fm,"You're joined already", Snackbar.LENGTH_LONG)
                            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_SLIDE)
                            .setAction("View") {
                                val intent = Intent(context, EventReviewActivity::class.java)
                                intent.putExtra("eventId", eventId)
                                startActivity(intent)
                            }
                            .show()
                    }
                }
            }
        } //end apply.
    }

    private fun getUserName(callback: (String) -> Unit) {

        val userRef = mDatabase.collection("Users").document(userId)
        userRef.addSnapshotListener { value, error ->

            error.let {
                //get username from uid
                val userName = value?.get("display_name").toString()
                Log.d("TAG", "getUserName: $userName")
                callback(userName)
            }
        }
    }


}