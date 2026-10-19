import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import "./ProviderBookings.css";
import { FaClock, FaCalendarAlt, FaUserTie, FaCheckCircle, FaTimesCircle, FaUserCircle, FaCarSide, FaTools, FaRoute } from "react-icons/fa";

export default function ProviderBookings() {
  const navigate = useNavigate();
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const providerId = localStorage.getItem("providerId");

  useEffect(() => {
    if (providerId) {
      fetchBookings();
    } else {
      setError("Provider ID not found. Please login again.");
      setLoading(false);
    }
  }, []);

  const fetchBookings = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await axios.get(
        `http://localhost:8080/booking/provider/${providerId}`
      );
      setBookings(res.data || []);
    } catch (error) {
      console.error("Error fetching bookings:", error);
      setError("Failed to load booking requests: " + (error.response?.data || error.message));
    } finally {
      setLoading(false);
    }
  };

  const acceptBooking = async (id) => {
    try {
      await axios.put(`http://localhost:8080/booking/${id}/status`, {
        status: "Accepted"
      });
      alert("Booking Accepted!");
      fetchBookings();
    } catch (error) {
      console.error("Error accepting booking:", error);
      alert("Failed to accept booking");
    }
  };

  const rejectBooking = async (id) => {
    const reason = window.prompt("Reason for rejecting this booking (optional):");
    if (reason === null) return; // user clicked cancel on prompt

    try {
      await axios.put(`http://localhost:8080/booking/${id}/status`, {
        status: "Rejected",
        note: reason || "Provider unavailable"
      });
      alert("Booking Rejected.");
      fetchBookings();
    } catch (error) {
      console.error("Error rejecting booking:", error);
      alert("Failed to reject booking");
    }
  };

  const startTripBooking = async (id) => {
    try {
      await axios.put(`http://localhost:8080/booking/${id}/status`, {
        status: "On The Way"
      });
      alert("Status updated: You are on the way! Customer has been notified.");
      fetchBookings();
    } catch (error) {
      console.error("Error updating status:", error);
      alert("Failed to update status to On The Way");
    }
  };

  const startServiceBooking = async (id) => {
    try {
      await axios.put(`http://localhost:8080/booking/${id}/status`, {
        status: "In Progress"
      });
      alert("Status updated: Service is in progress!");
      fetchBookings();
    } catch (error) {
      console.error("Error starting service:", error);
      alert("Failed to update status to In Progress");
    }
  };

  const completeBooking = async (id) => {
    const confirm = window.confirm("Mark this booking as completed?");
    if (!confirm) return;

    try {
      await axios.put(`http://localhost:8080/booking/${id}/status`, {
        status: "Completed"
      });
      alert("Booking marked as Completed! Customer notified to leave a review.");
      fetchBookings();
    } catch (error) {
      console.error("Error completing booking:", error);
      alert("Failed to complete booking");
    }
  };

  return (
    <div className="provider-bookings-container">
      <div className="bookings-header">
        <h1>Booking Requests</h1>
        <p>Manage and respond to customer service requests</p>
      </div>

      {loading && <p className="loading-state">Loading booking requests...</p>}

      {error && (
        <div className="error-state">
          <p>{error}</p>
          <button onClick={fetchBookings} className="retry-btn">Retry</button>
        </div>
      )}

      {!loading && !error && (
        <div className="bookings-list">
          {bookings.length === 0 ? (
            <p className="no-bookings">No booking requests yet.</p>
          ) : (
            bookings.map((booking) => (
              <div className="booking-card" key={booking.id}>
                <div className="booking-header">
                  <h3>{booking.serviceName}</h3>
                  <span className={`status ${booking.status.toLowerCase()}`}>
                    {booking.status}
                  </span>
                </div>

                <div className="customer-profile-section">
                  <div className="customer-avatar">
                    {booking.customerProfileImage ? (
                      <img 
                        src={booking.customerProfileImage} 
                        alt="Customer" 
                        className="customer-profile-img" 
                        onError={(e) => {
                          e.target.style.display = 'none';
                          e.target.onerror = null;
                        }} 
                      />
                    ) : (
                      <FaUserCircle size={50} color="#0A4D68" />
                    )}
                  </div>
                  <div className="customer-info">
                    <p className="customer-name"><strong>{booking.customerName || `ID: ${booking.customerId}`}</strong></p>
                    {booking.customerPhone && <p className="customer-phone">📱 {booking.customerPhone}</p>}
                    {booking.customerEmail && <p className="customer-email">✉️ {booking.customerEmail}</p>}
                  </div>
                </div>
                <p><FaCalendarAlt /> Date: {booking.date}</p>
                <p><FaClock /> Time: {booking.time}</p>
                {booking.totalAmount && <p>💰 Amount: ₹{booking.totalAmount}</p>}
                {booking.notes && <p>📝 Notes: {booking.notes}</p>}

                <div className="action-buttons">
                  {/* Stage 1: Pending -> Accept or Reject */}
                  {booking.status === "Pending" && (
                    <>
                      <button 
                        className="accept-btn" 
                        onClick={() => acceptBooking(booking.id)}
                      >
                        <FaCheckCircle /> Accept
                      </button>
                      <button 
                        className="reject-btn" 
                        onClick={() => rejectBooking(booking.id)}
                      >
                        <FaTimesCircle /> Reject
                      </button>
                    </>
                  )}

                  {/* Stage 2: Accepted -> Start Trip (On The Way) or Cancel */}
                  {booking.status === "Accepted" && (
                    <>
                      <button 
                        className="ontheway-btn" 
                        onClick={() => startTripBooking(booking.id)}
                      >
                        <FaCarSide /> Start Trip (On The Way)
                      </button>
                      <button 
                        className="reject-btn" 
                        onClick={() => rejectBooking(booking.id)}
                      >
                        <FaTimesCircle /> Cancel
                      </button>
                    </>
                  )}

                  {/* Stage 3: On The Way -> Start Service (In Progress) */}
                  {booking.status === "On The Way" && (
                    <button 
                      className="inprogress-btn" 
                      onClick={() => startServiceBooking(booking.id)}
                    >
                      <FaTools /> Start Service
                    </button>
                  )}

                  {/* Stage 4: In Progress -> Mark Complete */}
                  {booking.status === "In Progress" && (
                    <button 
                      className="complete-btn" 
                      onClick={() => completeBooking(booking.id)}
                    >
                      <FaCheckCircle /> Mark Complete
                    </button>
                  )}

                  {/* Live Tracker Link */}
                  <button
                    className="view-tracker-btn"
                    onClick={() => navigate(`/track-booking/${booking.id}`)}
                  >
                    <FaRoute /> Live Tracker
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
