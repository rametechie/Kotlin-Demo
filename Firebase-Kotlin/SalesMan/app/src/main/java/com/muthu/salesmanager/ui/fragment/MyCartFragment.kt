package com.muthu.salesmanager.ui.fragment

import android.app.ProgressDialog
import android.content.Context
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.muthu.salesmanager.R
import com.muthu.salesmanager.model.Order
import com.muthu.salesmanager.model.Product
import com.muthu.salesmanager.ui.adapter.MyCartListAdapter
import kotlinx.android.synthetic.main.fragment_my_cart.*
import kotlinx.android.synthetic.main.layout_address_detail.*
import kotlinx.android.synthetic.main.layout_address_detail.view.*
import kotlinx.android.synthetic.main.layout_payment.*
import android.text.format.DateFormat.getLongDateFormat
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class MyCartFragment : Fragment() {

    interface OnMyCartActionListener {
        fun onPaymentSuccess()
    }

    var products: ArrayList<Product> = arrayListOf()

    private var mDatabase: DatabaseReference? = null

    private var mAuth: FirebaseAuth? = null

    private var listItemsView: LinearLayout? = null

    private var totalPrice: TextView? = null

    private var step: Int = 0

    private var totalItemCount: Int = 0

    var mListener: OnMyCartActionListener? = null;

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        if (context is OnMyCartActionListener) {
            mListener = context
        }

        if (arguments != null) {
            var productList: ArrayList<Product> = arguments!!["products"] as ArrayList<Product>

            products.addAll(productList)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        var view: View = inflater.inflate(R.layout.fragment_my_cart, container, false)

        mAuth = FirebaseAuth.getInstance()

        mDatabase = FirebaseDatabase.getInstance().reference

        listItemsView = view.findViewById(R.id.product_list)

        totalPrice = view.findViewById(R.id.total_price)

        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        pay.setOnClickListener {

            if (step == 1) {
                if (shopName.editableText.isEmpty()) {
                    showAlert("Enter shop name")
                    return@setOnClickListener
                }
                if (mobileNumber.editableText.isEmpty()) {
                    showAlert("Enter mobile number")
                    return@setOnClickListener
                }
                if (address.editableText.isEmpty()) {
                    showAlert("Enter address")
                    return@setOnClickListener
                }
                if (pincode.editableText.isEmpty()) {
                    showAlert("Enter pincode")
                    return@setOnClickListener
                }
            } else if (step == 2) {
                doPayment()
                return@setOnClickListener
            }

            step += 1

            updateViews()
        }

        cart.setOnClickListener {
            if (step == 1 || step == 2) {
                step = 0

                updateViews()
            }
        }

        place.setOnClickListener {
            if (step == 2) {
                step = 1

                updateViews()
            }
        }

        updateViews()
    }

    private fun doPayment() {
//        showProgressDialog()

        val key = mDatabase?.child("orders")?.push()?.key

        var usetId: String = mAuth?.currentUser?.uid!!

        var order: Order = Order()

        order.shopName = shopName.editableText.toString()
        order.products.addAll(products)
        order.address = address.editableText.toString()
        order.pincode = address.editableText.toString()
        order.phone = mobileNumber.editableText.toString()
        order.totalAmount = totalPrice?.text.toString()
        order.totalProduct = totalItemCount
        order.userId = usetId
        order.userName = mAuth?.currentUser?.displayName
        val longDateFormat = SimpleDateFormat("M/d/yy h:mm a").format(Date())
        order.dateTime = longDateFormat


        mDatabase?.child("orders")?.child(key)?.setValue(order)
        mDatabase?.child("user-orders")?.child(usetId)?.child(key)?.setValue(order)

        for (product in products) {
            val globalPostRef = mDatabase?.child("products")?.child(product.id)

            globalPostRef?.runTransaction(object : Transaction.Handler {
                override fun onComplete(databaseError: DatabaseError?, b: Boolean, dataSnapShot: DataSnapshot?) {

                }

                override fun doTransaction(mutableData: MutableData?): Transaction.Result {

                    val p = mutableData?.getValue<Product>(Product::class.java)
                            ?: return Transaction.success(mutableData)

                    p?.let {
                        var countValue = product.stars.get(usetId)

                        countValue?.let { value ->

                            if (value > 0) {
                                var newCount = product.availableCount - value

                                if (newCount <= 0) {
                                    p.availableCount = 0;
                                    p.isAvailable = false
                                } else {
                                    p.availableCount = newCount;
                                }
                            }
                        }

                        if (it.stars.containsKey(usetId)) {
                            it.stars.remove(usetId)
                        }
                    }

                    mutableData.value = p

                    return Transaction.success(mutableData)
                }

            })
        }

        mListener?.let {
            it.onPaymentSuccess()
        }
//        hideProgressDialog()
    }

    var mProgressDialog: ProgressDialog? = null

    fun showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialog(activity)
            mProgressDialog!!.setMessage(getString(R.string.loading))
            mProgressDialog!!.isIndeterminate = true
        }

        mProgressDialog!!.show()
    }

    fun hideProgressDialog() {
        if (mProgressDialog != null && mProgressDialog!!.isShowing) {
            mProgressDialog!!.dismiss()
        }
    }

    private fun updateItems() {
        step = 0;



        listItemsView?.removeAllViews()

        var usetId: String = mAuth?.currentUser?.uid!!

        val inflater = LayoutInflater.from(activity)

        var priceTotalValue: Float = 0.0f

        for (product in products) {
            var itemView: View = inflater.inflate(R.layout.layout_holder_my_cart_list, null)

            var titleView: TextView = itemView.findViewById(R.id.product_title)
            var count: TextView = itemView.findViewById(R.id.count)
            var total: TextView = itemView.findViewById(R.id.total)

            titleView.setText(product.productName)

            count.setText("0")

            product.let {
                var countValue = product.stars.get(usetId)

                countValue?.let { value ->

                    count.setText("" + value + " x Rs." + product.price)


                    var productTotalPrice = value * product.price

                    total.setText("Rs." + productTotalPrice)

                    priceTotalValue = priceTotalValue + productTotalPrice
                }
            }

            listItemsView?.addView(itemView)
        }

        totalPrice?.setText("Rs." + priceTotalValue)

        addressLayout.visibility = View.GONE
        listItemsView?.visibility = View.VISIBLE
        paymentLayout.visibility = View.GONE

    }

    fun updateAddress() {
        step = 1

        addressLayout.visibility = View.VISIBLE
        listItemsView?.visibility = View.GONE
        paymentLayout.visibility = View.GONE
    }

    fun updatePayment() {
        step = 2

        payment_product_list?.removeAllViews()

        var usetId: String = mAuth?.currentUser?.uid!!

        val inflater = LayoutInflater.from(activity)

        var priceTotalValue: Float = 0.0f

        totalItemCount = 0;

        for (product in products) {
            var itemView: View = inflater.inflate(R.layout.layout_holder_my_cart_list, null)

            var titleView: TextView = itemView.findViewById(R.id.product_title)
            var count: TextView = itemView.findViewById(R.id.count)
            var total: TextView = itemView.findViewById(R.id.total)

            titleView.setText(product.productName)

            count.setText("0")

            product.let {
                var countValue = product.stars.get(usetId)

                countValue?.let { value ->

                    totalItemCount += value

                    count.setText("" + value + " x Rs." + product.price)

                    var productTotalPrice = value * product.price

                    total.setText("Rs." + productTotalPrice)

                    priceTotalValue = priceTotalValue + productTotalPrice
                }
            }

            payment_product_list?.addView(itemView)
        }

        paymentDetailAddress.setText(shopName.editableText.toString() + " \n " +
                address.editableText.toString() + " \n " +
                pincode.editableText.toString() + " \n " +
                mobileNumber.editableText.toString() + " \n "
        )

        addressLayout.visibility = View.GONE
        listItemsView?.visibility = View.GONE
        paymentLayout.visibility = View.VISIBLE
    }

    private fun updateViews() {
        when (step) {
            0 -> {
                cart.setImageResource(R.drawable.ic_shopping_cart_color)
                place.setImageResource(R.drawable.ic_place)
                payment.setImageResource(R.drawable.ic_credit_card)

                pay.setText("Next - Shipping")

                updateItems()
            }
            1 -> {
                cart.setImageResource(R.drawable.ic_shopping_cart_color)
                place.setImageResource(R.drawable.ic_place_color)
                payment.setImageResource(R.drawable.ic_credit_card)

                pay.setText("Next - Payment")

                updateAddress()
            }
            2 -> {

                cart.setImageResource(R.drawable.ic_shopping_cart_color)
                place.setImageResource(R.drawable.ic_place_color)
                payment.setImageResource(R.drawable.ic_credit_card_color)

                pay.setText("Confirm")

                updatePayment()
            }
        }
    }

    fun showAlert(message: String) {
        Snackbar.make(rootView!!, message, Snackbar.LENGTH_SHORT)
                .show();
    }
}
