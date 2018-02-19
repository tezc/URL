import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic URLParser implementation based on RFC 3986
 *
 *    The following are two example URIs and their component parts:

 * foo://user:password@example.com:8042/over/there?name=ferret#nose
 * \_/   \____________________________/\_________/ \_________/ \__/
 *  |                |                     |            |       |
 * scheme        authority                path        query   fragment
 *
 * --------------------------------------------------------------------
 *
 * URL defined as 5 main components as stated above,
 * authority component contains 3 component itself
 * This definition represented as separate classes in this
 * implementation.
 *
 * Some required parts are skipped as it makes project scope grow
 * IP validation(v4 & v6),
 * URI reference resolution(therefore Path component validation)
 *
 */
public class URL
{
    private static final Map<String, String> DEFAULT_SCHEME_PORTS;
    static {
        DEFAULT_SCHEME_PORTS = new HashMap<>();

        DEFAULT_SCHEME_PORTS.put("http",  "80");
        DEFAULT_SCHEME_PORTS.put("https", "443");
    }

    private static class Authority
    {
        private CharSequence userInfo;
        private CharSequence domain;
        private CharSequence port;

        private Authority(CharSequence userInfo, CharSequence domain,
                                                 CharSequence port)
        {
            this.userInfo = userInfo;
            this.domain   = domain;
            this.port     = port;
        }
    }

    private final String url;

    private CharSequence scheme;
    private Authority authority;
    private CharSequence path;
    private CharSequence query;
    private CharSequence fragment;

    private Map<CharSequence, QueryItem> queryItems; //Key value queries in order



    /**
     * Parse url conforming to RFC3986
     *
     * @param url Url string to parse
     * @exception IllegalArgumentException if url param does not conform
     *                                     to RFC 3986 rules
     */
    public URL(String url)
    {
        this.url = url;
        this.queryItems = new LinkedHashMap<>();

        parse();
    }

    /**
     * @return    parsed URL's scheme
     */
    public CharSequence getScheme()
    {
        return scheme;
    }

    /**
     * @return    parsed URL's userInfo
     */
    public CharSequence getUserInfo()
    {
        return authority == null ? null : authority.userInfo;
    }

    /**
     * @return    parsed URL's domain
     */
    public CharSequence getDomain()
    {
        return authority == null ? null : authority.domain;
    }

    /**
     * @return    parsed URL's port
     */
    public CharSequence getPort()
    {
        return authority == null ? null : authority.port;
    }

    /**
     * @return    parsed URL's path
     */
    public CharSequence getPath()
    {
        return path;
    }

    /**
     * @return    parsed URL's query
     */
    public CharSequence getQuery()
    {
        return query;
    }

    /**
     * @return    parsed URL's fragment
     */
    public CharSequence getFragment()
    {
        return fragment;
    }

    /**
     * Get query item map if any.
     * Any query key may hold multiple values
     *
     * @return Query map
     */
    public Map<CharSequence, QueryItem> getQueryItems()
    {
        return queryItems;
    }

    /**
     *
     * @param c character to check against s
     * @param s character list to search c in
     * @return true if one of s' characters is equal to c
     *         false otherwise
     *
     */
    private static boolean isOneOfThem(char c, String s)
    {
        for (int i = 0; i < s.length(); i++) {
            if (c == s.charAt(i)) {
                return true;
            }
        }

        return false;
    }

    /**
     * RFC 3986 2.2. Reserved Chars
     *
     * sub-delims  = "!" / "$" / "&" / "'" / "(" / ")"
     *                   / "*" / "+" / "," / ";" / "="
     */
    private static boolean isSubDelim(char c)
    {
        return isOneOfThem(c, "!$&'()*+,;=");
    }

    /**
     *   RFC 3986 2.3. Unreserved Chars
     *
     *   unreserved  = ALPHA / DIGIT / "-" / "." / "_" / "~"
     */
    private static boolean isUnreserved(char c)
    {
        return Character.isAlphabetic(c) ||
            Character.isDigit(c) || isOneOfThem(c, "-._~");
    }

    /**
     * Hex Digit = 0 to F
     */
    private static boolean isHexDigit(char c)
    {
        return Character.digit(c, 16) != -1;
    }

    /**
     * pct-encoded   = "%" HEXDIG HEXDIG
     */
    private static boolean isPctEncoded(String s, int pos)
    {
        if (s.charAt(pos) == '%' && pos + 2 != s.length()) {
            if (isHexDigit(s.charAt(pos + 1)) &&
                isHexDigit(s.charAt(pos + 2))) {
                return true;
            }
        }

        return false;
    }

    /**
     * RFC 3986 Section 3.3.  Path
     *
     * pchar  = unreserved / pct-encoded / sub-delims / ":" / "@"
     *
     * @param  s,  string to check against
     * @param  pos pos of char to start checking
     * @return     -1      if not pchar
     *             pos + 1 if unreserved / sub-delims/ ':' / '@'
     *             pos + 3 if pct-encoded
     */
    private static int isPchar(String s, int pos)
    {
        char c = s.charAt(pos);
        if (!isUnreserved(c) && !isSubDelim(c) && !isOneOfThem(c, ":@")) {
            if (isPctEncoded(s, pos)) {
                return pos + 3;
            }

            return -1;
        }

        return pos + 1;
    }


    /**
     *
     * RFC 3986 3.1. Scheme
     *
     * An implementation should accept uppercase letters as
     * equivalent to lowercase in scheme names
     * (e.g., allow "HTTP" as well as "http") for the sake of
     * robustness but should only produce lowercase scheme names for
     * consistency.
     *
     * --------------------------------------
     *
     * We convert it to lowercase beforehand, later on decoding if we
     * encounter port component, we check that port number against scheme
     * default. Therefore, we need to convert now or later anyway.
     *
     * @param pos Position to end of scheme, must point to ':'
     * @return    Position to end of scheme so it will point one after
     *            ':' char.
     * @exception IllegalArgumentException  if scheme is already parsed
     *                                      if scheme component does not
     *                                      confirm to RFC syntax
     */
    private int parseScheme(int pos)
    {
        if (scheme != null) {
            throw new IllegalArgumentException(
                errorStr(pos, "Multiple scheme components"));
        }

        // Scheme validation starts
        // scheme      = ALPHA *( ALPHA / DIGIT / '+' / '-' / '.' )
        if (pos == 0 || !Character.isAlphabetic(url.charAt(0)) ) {
            throw new IllegalArgumentException(
                errorStr(0, "Scheme must start with alphabetic chars"));
        }

        int begin = 0;
        while (begin != pos) {
            char c = url.charAt(begin);
            if (!Character.isDigit(c) &&
                !Character.isAlphabetic(c) && !isOneOfThem(c, "+-.")) {
                throw new IllegalArgumentException(
                    errorStr(pos, "scheme must confirm to " +
                                  "ALPHA *( ALPHA / DIGIT / '+' / '-' / '.' )"));
            }

            begin++;
        }
        //Scheme validation end

        scheme = new CharBuffer(url.substring(0, pos).toLowerCase(), 0, pos);

        pos++; // Skip ':' char
        return pos;
    }

    /**
     *
     * @param begin Position to start of user-info
     * @param pos   Position to end of user-info, must point to '@' char
     * @return      Position to end of user info, so it will point one after
     *              '@' char.
     *
     * @exception IllegalArgumentException if user-info component does not
     *                                     confirm to RFC syntax
     */
    private int parseUserInfo(int begin, int pos)
    {
        final int PCT_ENCODED_LEN = 3;

        // User info validation
        // userinfo    = *( unreserved / pct-encoded / sub-delims / ":" )
        int i = begin;
        while (i != pos) {
            char c = url.charAt(i);
            if (!isUnreserved(c) && !isSubDelim(c) && c != ':') {
                if (isPctEncoded(url, i)) {
                    i += PCT_ENCODED_LEN;
                    continue;
                }
                throw new IllegalArgumentException(
                    errorStr(i, "user-info must confirm to " +
                           "*( unreserved / pct-encoded / sub-delims / ':' )"));
            }

            i++;
        }

        authority.userInfo = new CharBuffer(url, begin, pos);

        pos++; // Skip '@' char
        return pos;
    }


    /**
     * RFC 3986 Section 3.2
     * The authority component is preceded by a double slash ("//") and
     * is terminated by the next slash ("/"), question mark ("?"),
     * or number sign ("#") character, or by the end of the URI.
     *
     *
     * @param pos Position to start parsing, must point to "//" char couple
     * @return    Position to end of authority component
     *
     * @exception IllegalArgumentException if authority already parsed
     *                                     if authority component does not
     *                                     confirm to RFC syntax
     */
    private int parseAuthority(int pos)
    {
        if (authority != null) {
            throw new IllegalArgumentException(
                errorStr(pos, "Multiple authority components"));
        }

        authority = new Authority(null, null, null);

        boolean end = false;

        pos += "//".length(); //Skip authority start chars : "//",
        int colon = Integer.MAX_VALUE; //Port delimiter position
        int begin = pos;

        while (pos != url.length() && !end) {
            char c = url.charAt(pos);
            switch (c) {
                case '?':
                case '/':
                case '#':
                    end = true;
                    break;
                case ':':
                    /*
                     * Either this is port delimiter : 127.0.0.1:80
                     * or part of user-info section  : user:pass@host
                     * Keep it to seperate hostname and port quickly later
                     * If it is user-info part, we reset it to initial value
                     * when we encounter '@';
                     */
                    colon = pos;
                    pos++;
                    break;
                case '@':
                    pos = parseUserInfo(begin, pos);

                    //Colon we encountered before was user-info's, we track
                    //hostname:port colon encounter, so reset it back
                    colon = Integer.MAX_VALUE;

                    begin = pos;
                    break;
                default:
                    pos++;
                    break;
            }
        }


        final int domainEnd = Math.min(colon, pos);
        // Here omitting ipv4 or ipv6 validation
        authority.domain = new CharBuffer(url, begin, domainEnd);

        /*
         * RFC 3986 Section 3.3.3. Port
         *
         * A scheme may define a default port. For example, the "http"
         * scheme defines a default port of "80", corresponding to its
         * reserved TCP port number. URI producers and normalizers
         * should omit the port component and its ":" delimiter if
         * port is empty or if its value would be the same as that
         * of the scheme's default.
         */
        if (domainEnd != pos){
            CharBuffer port = new CharBuffer(url, domainEnd + 1, pos);
            if (!port.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException(
                    errorStr(domainEnd + 1, "Port must confirm to = *DIGIT"));
            }

            CharSequence defaultPort = DEFAULT_SCHEME_PORTS.get(scheme);
            if (defaultPort != null && defaultPort.equals(port)) {
                port = null;
            }

            authority.port = port;
        }

        return pos;
    }


    /**
     * RFC 3986 Section 3.3. Path
     *
     * The path is terminated by the first question mark ("?") or
     * number sign ("#") character, or by the end of the URI
     *
     * @param start start pos to indicate head of path component
     * @param end   position to end of path component, -1 if unknown
     * @return      Position to end of path component
     *
     * @exception IllegalArgumentException if path already parsed
     *                                     if path component does not
     *                                     confirm to RFC syntax
     */
    private int parsePath(int start, int end)
    {
        if (path != null) {
            throw new IllegalArgumentException(
                                  errorStr(start, "Multiple path components"));
        }

        if (end == -1) {
            //This is the case we need to find end of Path
            end = start;
            while (end != url.length()) {
                char c = url.charAt(end);
                if (c == '?' || c == '#') {
                    break;
                }
                end++;
            }
        }

        path = new CharBuffer(url, start, end);

        /*
         * RFC 3986 3.3.  Path
         * If a URI contains an authority component, then the path component
         * must either be empty or begin with a slash ("/") character
         */
        if (authority != null) {
            if (path.length() != 0 && path.charAt(0) != '/') {
                throw new IllegalArgumentException(
                    errorStr(start, "When authority present, path must " +
                                    "be empty string or start with /"));
            }
        }
        else {
            /*
             * RFC 3986 3.3.  Path
             * if a URI does not contain an authority component,
             * then the path cannot begin with two slash characters ("//")
             */
            if (path.length() > 1 && path.charAt(0) == '/' &&
                path.charAt(1) == '/') {
                throw new IllegalArgumentException(
                    errorStr(start, "When authority is not present, " +
                                    "path cannot start with //"));
            }
        }

        /*
         * RFC 3986 3.3.  Path
         *
         * In addition, a URI reference (Section 4.1) may be a relative-path
         * reference, in which case the first path segment cannot
         * contain a colon (":") character.
         *
         * ------------------------------
         *
         * Here I omit this check and reference uri resolution as
         *
         */

        return end;
    }

    /**
     * RFC 3986 Section 3.4. Query
     *
     * The query component is indicated by the first question mark ("?")
     * character and terminated by a number sign ("#") character or
     * by the end of the URI
     *
     * @param pos Position to start parsing, must point to '?' char
     * @return    Position to end of query component
     *
     * @exception IllegalArgumentException if query already parsed
     *                                     if query component does not
     *                                     confirm to RFC syntax
     */
    private int parseQuery(int pos)
    {
        if (query != null) {
            throw new IllegalArgumentException(
                           errorStr(pos, "Multiple query components"));
        }

        pos++; //Skip '?' char

        int begin = pos;
        while (pos != url.length()) {
            char c = url.charAt(pos);
            if (c == '#') {
                break;
            }

            // Query validation
            // query       = *( pchar / '/' / '?' )
            if (c != '/' && c != '?') {
                pos = isPchar(url, pos);
                if (pos == -1) {
                    throw new IllegalArgumentException(
                        errorStr(pos, "Query must confirm to " +
                                      "*( pchar / '/' / '?' )"));
                }
            }
            else {
                pos++;
            }
        }

        query = new CharBuffer(url, begin, pos);

        /*
         * '=' char expected as split char between key and value
         * INT_MAX is sentinel to indicate not yet '=' encountered
         */
        int split = Integer.MAX_VALUE;

        int curr = 0;
        int head = 0;

        do {
            char c;
            if (curr == query.length() || (c = query.charAt(curr)) == '&') {
                //Print even there is no key=value structure but key
                split = Math.min(split, curr);
                final int valueHead = Math.min(split + 1, curr);
                QueryItem item = new QueryItem(query, head, split,
                                                                 valueHead, curr);
                QueryItem prev = queryItems.putIfAbsent(item.key, item);
                if (prev != null) {
                    //There is an item with same key, tail this object as last
                    //object of its children
                    while (prev.next != null) {
                        prev = prev.next;
                    }
                    prev.next = item;
                }

                //Move to next key value pair
                head = curr + 1;
                split = Integer.MAX_VALUE;
            }
            else if (c == '=') {
                split = curr;
            }
        } while (curr++ != query.length());

        return pos;
    }


    /**
     * RFC 3986 Section 3.5. Fragment
     * A fragment identifier component is indicated by the presence of a
     * number sign ("#") character and terminated by the end of the URI.
     *
     *
     * @param pos Position to start parsing, must point to '#' char
     * @return    Position to end of fragment component which is URL end
     *
     * @exception IllegalArgumentException  if fragment component does not
     *                                      confirm to RFC syntax
     */
    private int parseFragment(int pos)
    {
        // As RFC defines every char after '#' as fragment, we should not
        // hit this case to parse fragment again, it could only be a bug
        assert (fragment == null);

        pos++; // Skip # char

        // Fragment validation
        // fragment    = *( pchar / "/" / "?" )
        int begin = pos;
        while (begin != url.length()) {
            char c = url.charAt(begin);
            if (!isOneOfThem(c, "/?")) {
                begin = isPchar(url, begin);
                if (begin == -1) {
                    throw new IllegalArgumentException(
                        errorStr(begin, "Fragment must confirm to" +
                                        " *( pchar / '/' / '?' )"));
                }
            }
            else {
                begin++;
            }
        }

        fragment = new CharBuffer(url, pos, url.length());

        return url.length();
    }


    /*
     * Used as reason string input to appropritate exceptions occured
     * while parsing URL
     */
    private String errorStr(int pos, String reason)
    {
        return "Malformed url : " + url +
                        ", at : " + pos +
                    ", Reason : " + reason;
    }

    /**
     * Main loop to parse URI to components
     *
     * RFC 3986 Section 2.4.When to Encode or Decode
     *
     * When a URI is dereferenced, the components and subcomponents
     * significant to the scheme-specific dereferencing process (if any)
     * must be parsed and separated before the percent-encoded octets within
     * those components can be safely decoded, as otherwise the data may be
     * mistaken for component delimiters.
     *
     * --------------------------------------------------------------------
     *
     * As this class is not scheme-specific, I omit decoding percentage
     * encoded parts
     *
     * Path component start is not indicated with a specific char, although
     * it is a required part in every URL, so my understanding is path
     * component existence may be detected either before or after
     * component encounters.
     *
     * scheme://authority/path?query#frag
     *
     * So,
     * 1 - if we parsed authority, next part is path
     * 2 - if we hit '?' and not parsed part yet, 'not parsed part' before
     *     '?' is path
     * 3 - if we hit '#', and not parsed path yet, 'not parsed part'  before
     *     '#' is path
     * 4 - if we hit end of URI and not parsed path yet, 'not parsed part'
     *     before end of URI is path.
     *
     * @exception IllegalArgumentException if URL does not confirm to RFC
     *                                     syntax
     */
    private void parse()
    {
        int pos = 0;   //Current pos on URL string
        int begin = 0; //Last string snippet head which is not parsed yet

        while (pos < url.length()) {
            char c = url.charAt(pos);

            /*
             * RFC 3986 Section 3 Syntax Components
             *
             * The scheme and path components are required, though the path
             * may be empty (no characters);
             *
             * So we check scheme before parsing any other components
             */
            switch (c) {
                case '?':
                    if (scheme == null) {
                        throw new IllegalArgumentException(
                                      errorStr(0, "No scheme found"));
                    }

                    if (path == null) {
                        pos = parsePath(begin, pos);
                    }
                    pos = parseQuery(pos);
                    break;

                case '#':
                    if (scheme == null) {
                        throw new IllegalArgumentException(
                                     errorStr(0, "No scheme found"));
                    }

                    if (path == null) {
                        pos = parsePath(begin, pos);
                    }
                    pos = parseFragment(pos);
                    begin = pos;
                    break;

                case ':':
                    pos = parseScheme(pos);
                    begin = pos;
                    break;

                case '/':
                    pos++;
                    //Check "//" condition to start parsing authority
                    if (pos != url.length() && url.charAt(pos) == '/') {

                        if (scheme == null) {
                            throw new IllegalArgumentException(
                                         errorStr(0, "No scheme found"));
                        }

                        //pass input to point at "//"
                        pos = parseAuthority(pos - 1);
                        //path starts at pos, end will be calculated
                        pos = parsePath(pos, -1);
                        begin = pos;
                    }

                    break;

                default:
                    pos++;
                    break;
            }
        }

        if (scheme == null) {
            throw new IllegalArgumentException(errorStr(0, "No scheme found"));
        }

        if (path == null) {
            parsePath(begin, pos);
        }
    }


    /**
     * Holds reference to a char sequence with start and end positions
     * This class is used to keep reference to a part of a String object
     * without creating unnecessary copy
     */
    private static class CharBuffer implements CharSequence
    {
        private final CharSequence src;
        private final int start;
        private final int end;
        private int hash;

        private CharBuffer(CharSequence src, int start, int end)
        {
            this.src = src;
            this.start = start;
            this.end = end;
        }

        @Override
        public int length()
        {
            return end - start;
        }

        @Override
        public char charAt(int index)
        {
            return src.charAt(start + index);
        }

        @Override
        public CharSequence subSequence(int start, int end)
        {
            return new CharBuffer(src, this.start + start, this.start + end);
        }

        @Override
        public int hashCode()
        {
            int h = 0;
            if (hash == 0 && end - start > 0) {
                for (int i = 0; i < end - start; i++) {
                    h = 31 * h + src.charAt(i);
                }

                hash = h;
            }

            return hash;
        }

        @Override
        public boolean equals(Object o)
        {
            if (o.getClass() != this.getClass()) {
                return false;
            }

            CharBuffer other = (CharBuffer) o;

            if (this.length() != other.length()) {
                return false;
            }

            for (int i = 0; i < length(); i++) {
                if (this.charAt(i) != other.charAt(i)) {
                    return false;
                }
            }

            return true;
        }
    }

    /**
     * Basicly this class instances keep reference to key and value snippets
     * of query components without creating string object.
     * <p>
     * http://domain/path?query1=param1&query2=param2
     * <p>
     * For example above, each item will hold reference to uri string in
     * String src and
     * first item = (src, 19, 25, 27, 32)
     * as "query1" starts at 19, ends at 25
     * as "param" starts at 27, ends at 32
     * <p>
     * CharBuffer gets a CharSequence and two integers : start and end
     * <p>
     * Also each item may hold a reference to next item, so, if this items
     * used with map implementations, using this reference field,
     * it may behave like "multi-map" implementations as each key will
     * refer to same object. Then, chaining could be used
     * <p>
     * For ex:
     * <p>
     * [key1, value1] [key2, value4] [key3, value5]
     * |                              |
     * |                            :next -> [key3, value6]
     * |
     * :next -> [key1, value2]
     * |
     * :next -> [key1, value3]
     * <p>
     * <p>
     * <p>
     * As a result, required output format which asks for same keys printed
     * together, might work as O(1 + k) worst-case
     * put = [ - Look up for same key from hashmap,
     * - if not exists, put directly
     * - if exists, append new node as next to prev ]
     * <p>
     * get = [ - Pull an object of a key,
     * - Use its next reference to get all objects with the same key ]
     */
    public static class QueryItem
    {
        public final CharSequence key;
        public final CharSequence value;

        public QueryItem next;


        private QueryItem(CharSequence src, int keyHead, int keyEnd,
                          int valueHead, int valueEnd)
        {
            this.key = new CharBuffer(src, keyHead, keyEnd);
            this.value = new CharBuffer(src, valueHead, valueEnd);
        }
    }
}
