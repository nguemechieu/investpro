package org.investpro;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import static org.investpro.TelegramClient.message;


public enum NETWORK_RESPONSE {
    SERVER_OK(200),
    CREATED(201),
    ACCEPTED(202),
    NO_CONTENT(204),
    RESET_CONTENT(205),

    //200 OK
    //Standard response for successful HTTP requests. The actual response will depend on the request method used. In a GET request, the response will contain an entity corresponding to the requested resource. In a POST request, the response will contain an entity describing or containing the result of the action.
//The request has been fulfilled, resulting in the creation of a new resource.[5]
    //           202 Accepted
    // The request has been accepted for processing, but the processing has not been completed. The request might or might not be eventually acted upon, and may be disallowed when processing occurs.
    NON_Authoritative_Information(203),  // Non-Authoritative Information (since HTTP/1.1)
    //Theserver is a transforming proxy (e.g. a Web accelerator) that received a 200 OK from its origin, but is returning a modified version of the origin's response.[6][7]

    //The server successfully processed the request, and is not returning any content.
    RESTING_CONTENT_(205),// Reset ContentThe server successfully processed the request, asks that the requester reset its document view, and is not returning any content.
    PARTIAL_CONTENT_DELIVERED(206),// Partial Content The server is delivering only part of the resource (byte serving) due to a range header sent by the client. The range header is used by HTTP clients to enable resuming of interrupted downloads, or split a download into multiple simultaneous streams.
    MULTI_STATUS_(207),// Multi-Status (WebDAV; RFC 4918)The message body that follows is by default an XML message and can contain a number of separate response codes, depending on how many sub-requests were

    ALREADY_REPORTED(208),//    208 Already Reported (WebDAV; RFC 5842)
    //The members of a DAV binding have already been enumerated in a preceding part of the (multistatus) response, and are not being included again.
    IN_USE(226),//       226 IM Used (RFC 3229)The server has fulfilled a request for the resource, and the response is a representation of the result of one or more instance-manipulations applied to the current instance.[9]3xx redirection
    //      This class of status code indicates the client must take additional action to complete the request. Many of these status codes are used in URL redirection.[1]

    //    A user agent may carry out the additional action with no user interaction only if the method used in the second request is GET or HEAD. A user agent may automatically redirect a request. A user agent should detect and intervene to prevent cyclical redirects.[10]

    MULTIPLE_CHOICES(300),// Multiple Choices
    //Indicates multiple options for the resource from which the client may choose (via agent-driven content negotiation). For example, this code could be used to present multiple video format options, to list files with different filename extensions, or to suggest word-sense disambiguation.
    PERMANENTLY_MOVED(301),//      301 Moved Permanently
    //This and all future requests should be directed to the given URI.
    FOUND_Previously_Moved_Temporarily(302),//      302 Found (Previously "Moved temporarily")
    //  Tells the client to look at (browse to) another URL. The HTTP/1.0 specification (RFC 1945) required the client to perform a temporary redirect with the same method (the original describing phrase was "Moved Temporarily"),[11] but popular browsers implemented 302 redirects by changing the method to GET. Therefore, HTTP/1.1 added status codes 303 and 307 to distinguish between the two behaviours.[10]
    OTHER(303),    //303 //See Other (since HTTP/1.1)
    //The response to the request can be found under another URI using the GET method. When received in response to a POST (or PUT/DELETE), the client should presume that the server has received the data and should issue a new GET request to the given URI.
    NOT_MODIFIED(304),//   304 Not ModifiedIndicates that the resource has not been modified since the version specified by the request headers If-Modified-Since or If-None-Match. In such case, there is no need to retransmit the resource since the client still has a previously-downloaded copy.
    RESOURCE_REQUESTED_ONLY_PROXY_AVAILABLE(305),// 305 Use Proxy (since HTTP/1.1)
    //The requested resource is available only through a proxy, the address for which is provided in the response. For security reasons, many HTTP clients (such as Mozilla Firefox and Internet Explorer) do not obey this status code.[12]
    NO_LONGER_USE(306),// 306 Switch ProxyNo longer used. Originally meant "Subsequent requests should use the specified proxy."
    TEMPORALY_REDIRECTED(307),// Temporary Redirect (since HTTP/1.1) In this case, the request should be repeated with another URI; however, future requests should still use the original URI. In contrast to how 302 was historically implemented, the request method is not allowed to be changed when reissuing the original request. For example, a POST request should be repeated using another POST request.
    PERMANENTLY_REDIRECTED(308),//308 Permanent RedirectThis and all future requests should be directed to the given URI. 308 parallel the behaviour of 301, but does not allow the HTTP method to change. So, for example, submitting a form to a permanently redirected resource may continue smoothly.
    //4xx client errors
    //      A The Wikimedia 404 message
    ERROR_ON_WIKIPEDIA(404),//error on Wikimedia This class of status code is intended for situations in which the error seems to have been caused by the client. Except when responding to a HEAD request, the server should include an entity containing an explanation of the error situation, and whether it is a temporary or permanent condition. These status codes are applicable to any request method. User agents should display any included entity to the user.

    BAD_REQUEST_SUBMITTED(400),//400 Bad Request
    //  The server cannot or will not process the request due to an apparent client error (e.g., malformed request syntax, size too large, invalid request message framing, or deceptive request routing).
    UNAUTHORIZE_REQUEST(401),//        401 Unauthorized
    FORBIDDEN(403),// Similar to 403 Forbidden, but specifically for use when authentication is required and has failed or has not yet been provided. The response must include a WWW-Authenticate header field containing a challenge applicable to the requested resource. See Basic access authentication and Digest access authentication. 401 semantically means "unauthorised", the user does not have valid authentication credentials for the target resource.
    // Note: Some sites incorrectly issue HTTP 401 when an IP address is banned from the website (usually the website domain) and that specific address is refused permission to access a website.[citation needed]
    PAYMENT_REQIRED(402),//  402 Payment Required
    //       Reserved //for future use. The original intention was that this code might be used as part of some form of digital cash or micropayment scheme, as proposed, for example, by GNU Taler,[13] but that has not yet happened, and this code is not widely used. Google Developers API uses this status if a particular developer has exceeded the daily limit on requests.[14] Sipgate uses this code if an account does not have sufficient funds to start a call.[15] Shopify uses this code when the store has not paid their fees and is temporarily disabled.[16] Stripe uses this code for failed payments where parameters were correct, for example blocked fraudulent payments.[17]
    //             403 Forbidden
    //   The request contained valid data and was understood by the server, but the server is refusing action. This may be due to the user not having the necessary permissions for a resource or needing an account of some sort, or attempting a prohibited action (e.g. creating a duplicate record where only one is allowed). This code is also typically used if the request provided authentication by answering the WWW-Authenticate header field challenge, but the server did not accept that authentication. The request should not be repeated.
    RESOURCE_NOT_FOUND(404),//         404 Not Found
    //The requested resource could not be found but may be available in the future. Subsequent requests by the client are permissible.
    REQUEST_NOT_ALLOWED(405),//405 Method Not Allowed
    //A request method is not supported for the requested resource; for example, a GET request on a form that requires data to be presented via POST, or a PUT request on a read-only resource.
    REQUEST_NOT_ACCEPTABLE(406),//  406 Not Acceptable
    //The requested resource is capable of generating only content not acceptable according to the Accept headers sent in the request. See Content negotiation.
    PROXY_AUTHENTIFICATION_REQUIED(407),//407 Proxy Authentication Required
    // The client must first authenticate itself with the proxy.
    REQUEST_TIME_OUT(408),// Request Timeout
    //The server timed out waiting for the request. According to HTTP specifications: "The client did not produce a request within the time that the server was prepared to wait. The client MAY repeat the request without modifications at any later time."
    REQUEST_CONFLICT(409),//      409 Conflict
    //Indicates that the request could not be processed because of conflict in the current state of the resource, such as an edit conflict between multiple simultaneous updates.
    GONE(410),//      410 Gone Indicates that the resource requested was previously in use but is no longer available and will not be available again. This should be used when a resource has been intentionally removed and the resource should be purged. Upon receiving a 410 status code, the client should not request the resource in the future. Clients such as search engines should remove the resource from their indices. Most use cases do not require clients and search engines to purge the resource, and a "404 Not Found" may be used instead.
    REQUEST_DID_NOT_SPECIFIED_LENGTH(411),//       411 Length Required The request did not specify the length of its content, which is required by the requested resource.
    PRECONDITION_FAILDE(412),//  412 Precondition Failed
    //     The server does not meet one of the preconditions that the requester put on the request header fields.
    PLAY_LOAD_TOO_LARGE(413),//           413 Payload Too Large
    //The request is larger than the server is willing or able to process. Previously called "Request Entity Too Large" in RFC 2616.[18]
    URI_TOO_LONG(414),//      414 URI Too Long
    //The URI provided was too long for the server to process. Often the result of too much data being encoded as a query-string of a GET request, in which case it should be converted to a POST request. Called "Request-URI Too Long" previously in RFC 2616.[19]
    USUPPORTED_MEDIA(415),//      415 Unsupported Media Type
    // The request entity has a media type which the server or resource does not support. For example, the client uploads an image as image/svg+xml, but the server requires that images use a different format.
    RANGE_NOT_SPECIFIED(416),     //416 Range Not Satisfiable
    //The client has asked for a portion of the file (byte serving), but the server cannot supply that portion. For example, if the client asked for a part of the file that lies beyond the end of the file. Called "Requested Range Not Satisfiable" previously RFC 2616.[20]
    EXPECTATION_FAILED(417),//417 Expectation Failed The server cannot meet the requirements of the Expect request-header field.[21]
    IM_TEA_POT(418),//      418 I'm a teapot (RFC 2324, RFC 7168)
    MISDIRECTED_REQUEST(421),//421 Misdirected Request
    //This code was defined in 1998 as one of the traditional IETF April Fools' jokes, in RFC 2324, Hyper Text Coffee Pot Control Protocol, and is not expected to be implemented by actual HTTP servers. The RFC specifies this code should be returned by teapots requested to brew coffee.[22] This HTTP status is used as an Easter egg in some websites, such as Google.com's "I'm a teapot" easter egg.[23][24][25]
    //  421 Misdirected Request
    // The request was directed at a server that is not able to produce a response (for example because of connection reuse).
    UNSUPPORTED_ENTITY(422),//       422 Unprocessable Entity
    //The request was well-formed but was unable to be followed due to semantic errors.[8]
    RESOURCES_IS_LOCKED(423),//      423 Locked (WebDAV; RFC 4918)
    //The resource that is being accessed is locked.[8]
    FAILED_DEPENDENCY(424),// 425 Failed 424 Failed Dependency (WebDAV; RFC 4918) The request failed because it depended on another request and that request failed (e.g., a PROPPATCH).[8]
    REQUEST_TOO_EARLY(425),//    425 Too Early (RFC 8470) Indicates that the server is unwilling to risk processing a request that might be replayed.

    RESOURCE_REQUIRE_UPGRADE(426),// Upgrade Required
    // The client should switch to a different protocol such as TLS/1.3, given in the Upgrade header field.
    PRECONDITION_REQUIRED(428),//       428 Precondition Required (RFC 6585)
    //The origin server requires the request to be conditional. Intended to prevent the 'lost update' problem, where a client GETs a resource's state, modifies it, and PUTs it back to the server, when meanwhile a third party has modified the state on the server, leading to a conflict.[26]
    TOO_MANY_REQUEST_SEND(429),//      429 Too Many Requests (RFC 6585)
    //The user has sent too many requests in a given amount of time. Intended for use with rate-limiting schemes.[26]
    REQUEST_HEADER_FIELD_TOO_LARGE(431),//    431 Request Header Fields Too Large (RFC 6585)
    //  The server is unwilling to process the request because either an individual header field, or all the header fields collectively, are too large.[26]
    RESOURCE_UNAVAILABLE_LEGAL_REASONS(451),//      451 Unavailable For Legal Reasons (RFC 7725)
    //   A server operator has received a legal demand to deny access to a resource or to a set of resources that includes the requested resource.[27] The code 451 was chosen as a reference to the novel Fahrenheit 451 (see the Acknowledgements in the RFC).
    //         5xx server errors
    //The server failed to fulfil a request.

    //Response status codes beginning with the digit "5" indicate cases in which the server is aware that it has encountered an error or is otherwise incapable of performing the request. Except when responding to a HEAD request, the server should include an entity containing an explanation of the error situation, and indicate whether it is a temporary or permanent condition. Likewise, user agents should display any included entity to the user. These response codes are applicable to any request method.

    INTERNAL_SERVER_ERRORS(500),//500 Internal Server Error
    //   A generic error message, given when an unexpected condition was encountered and no more specific message is suitable.
    REQUEST_NOT_IMPLEMENTED(501),//501 Not Implemented
    //The server either does not recognize the request method, or it lacks the ability to fulfil the request. Usually this implies future availability (e.g., a new feature of a web-service API).
    BAD_GATEWAY(502),//      // 502 Bad Gateway
    //    The server was acting as a gateway or proxy and received an invalid response from the upstream server.
    SERVICE_IS_UNAVAILABLE(503),//          503 Service Unavailable
    //The server cannot handle the request (because it is overloaded or down for maintenance). Generally, this is a temporary state.[28]
    GATEWAY_TIME_OUT(504),//      504 Gateway Timeout
    // The server was acting as a gateway or proxy and did not receive a timely response from the upstream server.
    HTTP_VERVION_NOT_SUPPORTED(505),//      505 HTTP Version Not Supported
    // The server does not support the HTTP protocol version used in the request.
    VARIANT_ALSO_NEGOTIATES(506),//       506 Variant Also Negotiates (RFC 2295) Transparent content negotiation for the request results in a circular reference.[29]
    INSUFFISANT_STORAGE(507),//      507 Insufficient Storage (WebDAV; RFC 4918)
    //  The server is unable to store the representation needed to complete the request.[8]
    LOOP_DETECTED(508),//        508 Loop Detected (WebDAV; RFC 5842)
    //The server detected an infinite loop while processing the request (sent instead of 208 Already Reported).
    NOT_EXTENTED(510),//      510 Not Extended (RFC 2774)Further extensions to the request are required for the server to fulfil it.[30]

    NETWORK_AUTHENTICATION_REQUIRED(511),//  511 Network Authentication Required (RFC 6585)
    //The client needs to authenticate to gain network access. Intended for use by intercepting proxies used to control access to the network (e.g., "captive portals" used to require agreement to Terms of Service before granting full Internet access via a Wi-Fi hotspot).[26]
    //  Unofficial codes
    //The following codes are not specified by any standard.

    PAGE_EXPIRED(419),//419 Page Expired (Laravel Framework)
    // Used by the Laravel Framework when a CSRF Token is missing or expired.
    METHOD_ERROR(420), NON_AUTHORITATIVE_INFORMATION(203), MULTI_STATUS(900), IM_USED(226), PERMANENT_REDIRECT(308), UNPROCESSABLE_ENTITY(420), LOCKED(421), UPGRADE_REQUIRED(423), INSUFFICIENT_STORAGE(507), INTERNAL_SERVER_ERROR(500), NOT_FOUND(400), UNAUTHORIZED(402), OK(200), REQUEST_TIMEOUT(408), SERVICE_UNAVAILABLE(502), BAD_REQUEST(400);
    //420 Method Failure (Spring Framework)
    //A deprecated response used by the Spring Framework when a method has failed.[31]
    //  420 //Enhance Your Calm (Twitter)


    NETWORK_RESPONSE(int i) {
    }

    @Contract(pure = true)
    public @Nullable NETWORK_RESPONSE value(int responseCode) {

        if (this.ordinal() == responseCode) {
            return this;
        }

        return null;
    }

    public boolean verify(int responseCode) {
        return this.ordinal() == responseCode;
    }


    public boolean compareTo(int responseCode) {
        return OK.ordinal() == responseCode;
    }

    public String getMessage() {
        return message;
    }
}