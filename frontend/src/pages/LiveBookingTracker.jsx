import React, { useEffect, useState, useCallback } from "react";
import { useParams, useNavigate } from "react-router-dom";
import axios from "axios";
import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import { MapContainer, TileLayer, Marker, Popup, Polyline } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import L from "leaflet";
import {
  FaArrowLeft,
  FaSyncAlt,
  FaCalendarAlt,
  FaClock,
  FaUserTie,
  FaPhoneAlt,
  FaEnvelope,
  FaMapMarkerAlt,
  FaCheckCircle,
  FaTimesCircle,
  FaCarSide,
  FaTools,
  FaCheckDouble,
  FaAward,
  FaStar,
  FaInfoCircle,
  FaShieldAlt,
  FaBolt
} from "react-icons/fa";
import "./LiveBookingTracker.css";

// Leaflet marker icons
const providerMarkerIcon = new L.Icon({
  iconUrl: "https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-blue.png",
  shadowUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png",
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

const customerMarkerIcon = new L.Icon({
  iconUrl: "https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png",
  shadowUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png",
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

export default function LiveBookingTracker() {
  const { bookingId } = useParams();
  const navigate = useNavigate();

  const [trackingData, setTrackingData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [wsConnected, setWsConnected] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [statusFlash, setStatusFlash] = useState(null);

  // Rating state (for when completed)
  const [ratingStars, setRatingStars] = useState(5);
  const [ratingReview, setRatingReview] = useState("");
  const [submittingRating, setSubmittingRating] = useState(false);
  const [ratingSubmitted, setRatingSubmitted] = useState(false);

  const userEmail =
    localStorage.getItem("customerEmail") ||
    localStorage.getItem("providerEmail") ||
    localStorage.getItem("adminEmail") ||
    "user@servicespot.com";

  // Fetch initial tracking data
  const fetchTracking = useCallback(async (isManual = false) => {
    if (!bookingId) return;
    try {
      if (isManual) setRefreshing(true);
      const res = await axios.get(`http://localhost:8080/booking/${bookingId}/tracking`);
      setTrackingData(res.data);
      setError(null);
    } catch (err) {
      console.error("Failed to load tracking data:", err);
      setError(err.response?.data?.message || err.response?.data || "Failed to load tracking data.");
    } finally {
      setLoading(false);
      if (isManual) setRefreshing(false);
    }
  }, [bookingId]);

  useEffect(() => {
    fetchTracking();
  }, [fetchTracking]);

  // Connect to WebSocket for real-time live tracking updates
  useEffect(() => {
    if (!bookingId) return;

    console.log(`📡 Connecting to live tracking WebSocket for booking #${bookingId}...`);
    const socket = new SockJS(`http://localhost:8080/ws-notifications?email=${encodeURIComponent(userEmail)}`);
    const client = new Client({
      webSocketFactory: () => socket,
      debug: (str) => {
        // Stomp debug log
      },
      reconnectDelay: 4000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000
    });

    client.onConnect = () => {
      console.log(`✅ WebSocket connected. Subscribing to /topic/booking/${bookingId}`);
      setWsConnected(true);

      // Subscribe to this specific booking's real-time topic
      client.subscribe(`/topic/booking/${bookingId}`, (message) => {
        try {
          const payload = JSON.parse(message.body);
          console.log("⚡ Real-time live tracking update received:", payload);
          setTrackingData(payload);

          // Show status flash toast
          setStatusFlash(`Status updated to: ${payload.status}`);
          setTimeout(() => setStatusFlash(null), 5000);

          // Audio notification cue
          try {
            const audio = new Audio("/notification-sound.mp3");
            audio.volume = 0.5;
            audio.play().catch(() => {});
          } catch {}
        } catch (e) {
          console.error("Error parsing WebSocket message:", e);
        }
      });

      // Also subscribe to user queue if present
      if (userEmail) {
        client.subscribe(`/user/${userEmail}/queue/booking-tracking`, (message) => {
          try {
            const payload = JSON.parse(message.body);
            if (payload && String(payload.bookingId) === String(bookingId)) {
              setTrackingData(payload);
              setStatusFlash(`Status updated: ${payload.status}`);
              setTimeout(() => setStatusFlash(null), 5000);
            }
          } catch (e) {
            console.error("Error parsing user tracking message:", e);
          }
        });
      }
    };

    client.onStompError = (frame) => {
      console.error("STOMP error:", frame);
      setWsConnected(false);
    };

    client.onWebSocketError = () => {
      setWsConnected(false);
    };

    client.activate();

    return () => {
      if (client) {
        client.deactivate();
      }
    };
  }, [bookingId, userEmail]);

  // Cancel booking handler
  const handleCancelBooking = async () => {
    const confirm = window.confirm("Are you sure you want to cancel this service request?");
    if (!confirm) return;

    try {
      await axios.put(`http://localhost:8080/booking/cancel/${bookingId}`);
      fetchTracking(true);
    } catch (err) {
      alert("Failed to cancel booking: " + (err.response?.data || err.message));
    }
  };

  // Submit Rating
  const handleSubmitRating = async () => {
    if (!ratingReview.trim()) {
      alert("Please enter a short review of your experience.");
      return;
    }

    try {
      setSubmittingRating(true);
      await axios.post("http://localhost:8080/api/rating/create", {
        bookingId: Number(bookingId),
        stars: ratingStars,
        review: ratingReview
      });
      setRatingSubmitted(true);
      alert("Thank you! Your rating has been submitted.");
    } catch (err) {
      alert("Failed to submit rating: " + (err.response?.data?.message || err.message));
    } finally {
      setSubmittingRating(false);
    }
  };

  if (loading) {
    return (
      <div className="tracking-page">
        <div className="tracking-loading-state">
          <div className="tracking-loading-spinner"></div>
          <h2>Loading Live Booking Tracker...</h2>
          <p>Connecting to real-time service spot network</p>
        </div>
      </div>
    );
  }

  if (error || !trackingData) {
    return (
      <div className="tracking-page">
        <div className="tracking-error-state">
          <FaTimesCircle size={54} color="#ef4444" style={{ marginBottom: "16px" }} />
          <h2>Unable to Load Tracking</h2>
          <p>{error || "Booking not found."}</p>
          <button className="retry-action-btn" onClick={() => fetchTracking(true)}>
            Try Again
          </button>
          <button className="back-btn" style={{ marginLeft: "12px" }} onClick={() => navigate(-1)}>
            Go Back
          </button>
        </div>
      </div>
    );
  }

  const {
    status,
    serviceName,
    bookingDate,
    bookingTime,
    totalAmount,
    notes,
    statusNote,
    steps = [],
    provider = {},
    customer = {},
    currentStepIndex
  } = trackingData;

  // Calculate percentage of track completion
  const getProgressPercent = () => {
    if (status === "Rejected" || status === "Cancelled") return 100;
    if (status === "Pending") return 15;
    if (status === "Accepted") return 38;
    if (status === "On The Way") return 65;
    if (status === "In Progress") return 85;
    if (status === "Completed") return 100;
    return 10;
  };

  // Step Icon mapping
  const getStepIcon = (key, state) => {
    if (state === "rejected") return <FaTimesCircle />;
    if (state === "cancelled") return <FaTimesCircle />;
    if (state === "completed") return <FaCheckCircle />;

    switch (key) {
      case "REQUESTED":
        return <FaCalendarAlt />;
      case "ACCEPTED":
        return <FaUserTie />;
      case "ON_THE_WAY":
        return <FaCarSide />;
      case "IN_PROGRESS":
        return <FaTools />;
      case "COMPLETED":
        return <FaAward />;
      default:
        return <FaCheckCircle />;
    }
  };

  // Status Badge Helper
  const getStatusBadgeClass = () => {
    const s = status.toLowerCase().replace(/\s+/g, "");
    return s;
  };

  // Format timestamp helper
  const formatStepTime = (dt) => {
    if (!dt) return null;
    try {
      const d = new Date(dt);
      return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    } catch {
      return null;
    }
  };

  // Latitude and Longitude for Map
  const providerLat = provider.latitude || 17.3850;
  const providerLng = provider.longitude || 78.4867;
  const customerLat = customer.latitude || (providerLat + 0.015);
  const customerLng = customer.longitude || (providerLng + 0.02);

  const mapCenter = [(providerLat + customerLat) / 2, (providerLng + customerLng) / 2];

  return (
    <div className="tracking-page">
      <div className="tracking-container">
        {/* Top Bar Navigation */}
        <div className="tracking-top-bar">
          <button className="back-btn" onClick={() => navigate(-1)}>
            <FaArrowLeft /> Back to Bookings
          </button>

          <div className="top-bar-actions">
            <div className={`ws-status-badge ${wsConnected ? "connected" : "connecting"}`}>
              <span className={`pulse-dot ${wsConnected ? "pulse" : ""}`}></span>
              {wsConnected ? "Live Tracking Connected" : "Connecting..."}
            </div>

            <button
              className={`refresh-btn ${refreshing ? "spinning" : ""}`}
              onClick={() => fetchTracking(true)}
              title="Refresh live status"
            >
              <FaSyncAlt /> Refresh
            </button>
          </div>
        </div>

        {/* Status Flash Alert */}
        {statusFlash && (
          <div className="live-status-flash">
            <FaBolt /> {statusFlash}
          </div>
        )}

        {/* Hero Card with Stepper */}
        <div className="tracking-hero-card">
          <div className="hero-glow-accent"></div>

          <div className="hero-header-row">
            <div className="booking-main-meta">
              <span className="booking-ref-badge">
                <FaShieldAlt /> BOOKING #{bookingId}
              </span>
              <h1>{serviceName}</h1>
            </div>

            <div className={`status-badge-hero ${getStatusBadgeClass()}`}>
              {status === "On The Way" && <FaCarSide />}
              {status === "In Progress" && <FaTools />}
              {status === "Completed" && <FaAward />}
              {status === "Accepted" && <FaCheckCircle />}
              {status === "Pending" && <FaClock />}
              {status === "Rejected" && <FaTimesCircle />}
              {status === "Cancelled" && <FaTimesCircle />}
              <span>{status}</span>
            </div>
          </div>

          {/* Stepper Progress Bar */}
          <div className="progress-stepper-container">
            <div className="progress-track-bar">
              <div
                className={`progress-track-fill ${status.toLowerCase()}`}
                style={{ width: `${getProgressPercent()}%` }}
              ></div>
            </div>

            {/* Stepper Steps (5 Stages) */}
            <div className="stepper-steps-wrapper">
              {steps.map((st) => (
                <div key={st.stepNumber} className={`stepper-step ${st.state}`}>
                  <div className="step-icon-bubble">
                    {getStepIcon(st.key, st.state)}
                  </div>
                  <div className="step-info-col">
                    <span className="step-title">{st.title}</span>
                    {st.timestamp && (
                      <span className="step-time">
                        {formatStepTime(st.timestamp)}
                      </span>
                    )}
                    <span className="step-desc">{st.description}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Contextual Box */}
          <div className={`stage-context-box ${getStatusBadgeClass()}`}>
            <div className="stage-context-text">
              {status === "Pending" && (
                <>
                  <h4>Waiting for Provider Confirmation</h4>
                  <p>Your service request has been sent to {provider.name || "the provider"}. You will be notified the moment they accept.</p>
                </>
              )}
              {status === "Accepted" && (
                <>
                  <h4>Booking Confirmed! 🎉</h4>
                  <p>{provider.name || "Provider"} has scheduled your appointment for {bookingDate} at {bookingTime}.</p>
                </>
              )}
              {status === "On The Way" && (
                <>
                  <h4>🚗 Provider is On The Way!</h4>
                  <p>{provider.name || "Your provider"} is traveling to your location. Please keep your phone accessible.</p>
                </>
              )}
              {status === "In Progress" && (
                <>
                  <h4>🔧 Service Currently In Progress</h4>
                  <p>{provider.name || "Your technician"} has arrived and is actively performing the service.</p>
                </>
              )}
              {status === "Completed" && (
                <>
                  <h4>✨ Service Completed Successfully!</h4>
                  <p>We hope you are delighted with the service. Please share your rating and review below.</p>
                </>
              )}
              {status === "Rejected" && (
                <>
                  <h4>Request Unable to Be Fulfilled</h4>
                  <p>{statusNote || "The service provider was unavailable for this time slot. You can easily discover other available professionals."}</p>
                </>
              )}
              {status === "Cancelled" && (
                <>
                  <h4>Service Request Cancelled</h4>
                  <p>This appointment has been cancelled. Reach out if you need assistance rescheduling.</p>
                </>
              )}
            </div>

            {status === "Pending" && (
              <button className="cancel-booking-btn" onClick={handleCancelBooking}>
                Cancel Request
              </button>
            )}

            {(status === "Rejected" || status === "Cancelled") && (
              <button className="submit-rating-btn-large" onClick={() => navigate("/book-service")}>
                Book Another Service
              </button>
            )}
          </div>
        </div>

        {/* Live Map Tracking Component (when On The Way or In Progress) */}
        {(status === "On The Way" || status === "In Progress" || status === "Accepted") && (
          <div className="live-map-card">
            <h3 className="card-title">
              <FaMapMarkerAlt /> Live Location & Route Map
            </h3>

            <div className="map-wrapper">
              <MapContainer
                center={mapCenter}
                zoom={13}
                style={{ height: "100%", width: "100%" }}
                scrollWheelZoom={false}
              >
                <TileLayer
                  attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                  url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                />

                {/* Provider Marker */}
                <Marker position={[providerLat, providerLng]} icon={providerMarkerIcon}>
                  <Popup>
                    <strong>{provider.name || "Provider"}</strong>
                    <br />
                    {status === "On The Way" ? "🚗 En Route to You" : "📍 Service Provider"}
                  </Popup>
                </Marker>

                {/* Customer Location Marker */}
                <Marker position={[customerLat, customerLng]} icon={customerMarkerIcon}>
                  <Popup>
                    <strong>Your Service Location</strong>
                    <br />
                    {customer.addressLine ? `${customer.doorNo || ""}, ${customer.addressLine}` : "Destination"}
                  </Popup>
                </Marker>

                {/* Connecting Polyline Route */}
                <Polyline
                  positions={[
                    [providerLat, providerLng],
                    [customerLat, customerLng]
                  ]}
                  color="#0A4D68"
                  dashArray="6, 8"
                  weight={4}
                />
              </MapContainer>
            </div>

            <div className="map-status-overlay">
              <div className="map-legend">
                <span className="legend-item">
                  <span className="legend-dot provider"></span> Provider Location
                </span>
                <span className="legend-item">
                  <span className="legend-dot customer"></span> Your Destination
                </span>
              </div>
              <div>
                {status === "On The Way" && "Estimated travel time: 15-25 minutes"}
              </div>
            </div>
          </div>
        )}

        {/* Details Grid */}
        <div className="tracking-grid">
          {/* Provider Card */}
          <div className="tracking-card">
            <h3 className="card-title">
              <FaUserTie /> Service Provider
            </h3>

            <div className="provider-detail-block">
              <div className="provider-photo-wrap">
                {provider.profileImage ? (
                  <img src={provider.profileImage} alt={provider.name} />
                ) : (
                  <div className="provider-avatar-placeholder">
                    <FaUserTie />
                  </div>
                )}
              </div>

              <div className="provider-meta">
                <h3>{provider.name || "Assigned Provider"}</h3>
                <span className="provider-service-pill">
                  {provider.serviceType || serviceName}
                </span>
                <div className="provider-rating-stars">
                  <FaStar /> 4.9 <span>(Trusted Pro)</span>
                </div>
              </div>
            </div>

            <div className="appointment-info-list">
              {provider.city && (
                <div className="info-item-row">
                  <span className="info-label">
                    <FaMapMarkerAlt /> Service Base
                  </span>
                  <span className="info-value">
                    {provider.city}, {provider.state}
                  </span>
                </div>
              )}
            </div>

            <div className="contact-actions-row">
              {provider.phone && (
                <a href={`tel:${provider.phone}`} className="contact-btn phone">
                  <FaPhoneAlt /> Call Provider
                </a>
              )}
              {provider.email && (
                <a href={`mailto:${provider.email}`} className="contact-btn email">
                  <FaEnvelope /> Email Provider
                </a>
              )}
            </div>
          </div>

          {/* Appointment Summary Card */}
          <div className="tracking-card">
            <h3 className="card-title">
              <FaCalendarAlt /> Appointment Summary
            </h3>

            <div className="appointment-info-list">
              <div className="info-item-row">
                <span className="info-label">
                  <FaCalendarAlt /> Scheduled Date
                </span>
                <span className="info-value">{bookingDate}</span>
              </div>

              <div className="info-item-row">
                <span className="info-label">
                  <FaClock /> Scheduled Time
                </span>
                <span className="info-value">{bookingTime}</span>
              </div>

              <div className="info-item-row">
                <span className="info-label">
                  <FaTools /> Service
                </span>
                <span className="info-value">{serviceName}</span>
              </div>

              {totalAmount && (
                <div className="info-item-row">
                  <span className="info-label">Total Amount</span>
                  <span className="info-value amount">₹{totalAmount}</span>
                </div>
              )}

              {customer.addressLine && (
                <div className="info-item-row">
                  <span className="info-label">
                    <FaMapMarkerAlt /> Destination
                  </span>
                  <span className="info-value">
                    {customer.doorNo ? `${customer.doorNo}, ` : ""}
                    {customer.addressLine}, {customer.city}
                  </span>
                </div>
              )}

              {notes && (
                <div className="info-item-row">
                  <span className="info-label">
                    <FaInfoCircle /> Booking Notes
                  </span>
                  <span className="info-value">{notes}</span>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Rating Form (Displays prominently when status is Completed) */}
        {status === "Completed" && (
          <div className="tracking-rating-card">
            {!ratingSubmitted ? (
              <>
                <h3>Rate Your Service Experience</h3>
                <p>How did {provider.name || "the provider"} perform on this service?</p>

                <div className="stars-selector">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <button
                      key={star}
                      type="button"
                      className={`star-btn ${star <= ratingStars ? "active" : ""}`}
                      onClick={() => setRatingStars(star)}
                    >
                      ★
                    </button>
                  ))}
                </div>

                <div>
                  <textarea
                    className="rating-review-box"
                    rows="3"
                    placeholder="Tell us about the quality of service, punctuality, and professionalism..."
                    value={ratingReview}
                    onChange={(e) => setRatingReview(e.target.value)}
                  ></textarea>
                </div>

                <button
                  className="submit-rating-btn-large"
                  onClick={handleSubmitRating}
                  disabled={submittingRating}
                >
                  {submittingRating ? "Submitting..." : "Submit Review & Rating"}
                </button>
              </>
            ) : (
              <div className="rating-done-badge">
                <FaCheckDouble /> Thank you! Your review has been submitted.
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
