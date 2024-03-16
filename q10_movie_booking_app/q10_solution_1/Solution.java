/* ****** Copy this default code to your local code editor
and after completing solution, paste it back here for testing ******** */
import java.util.*;

interface ShowObserver{
    void update(Show show);
}
interface ShowSubject{
    void addObserver(ShowObserver observer);
    void notifyAll(Show show);
}
public class Solution implements Q10MovieBookingInterface{
    private Helper10 helper;
    private CinemaManager cinemaManager;
    private ShowManager showManager;
    private CinemaLister cinemaLister;
    private ShowLister showLister;
    private TicketBookingManager bookingManager;
    
    public Solution(){}
    public void init(Helper10 helper) {
        this.helper=helper;
        cinemaLister=new CinemaLister();
        showLister=new ShowLister();
        cinemaManager=new CinemaManager();
        showManager=new ShowManager();
        showManager.addObserver(cinemaLister);
        showManager.addObserver(showLister);
        bookingManager= new TicketBookingManager();
    }

    public void addCinema(int cinemaId, int cityId,
        int screenCount, int screenRow, int screenColumn) {
      cinemaManager.addCinema(cinemaId, cityId,
              screenCount, screenRow, screenColumn);
    }

    public void addShow(int showId, int movieId, int cinemaId,
                 int screenIndex, long startTime, long endTime) {
       Cinema cinema=cinemaManager.getCinema(cinemaId);
       showManager.addShow(showId, movieId, cinema,
               screenIndex, startTime, endTime);
    }
    // returns list of booked tickets id as row-column string
    public List<String> bookTicket(String ticketId,
                        int showId, int ticketsCount) {
        Show show = showManager.getShow(showId);
        if(show==null) return new ArrayList<>();
        return bookingManager.bookTicket(ticketId, show, ticketsCount);
    }

    public boolean cancelTicket(String ticketId) {
        return bookingManager.cancelTicket(ticketId);
    }

    public int getFreeSeatsCount(int showId) {
        Show show = showManager.getShow(showId);
        if(show==null) return 0;
        return bookingManager.getFreeSeatsCount(show);
    }

    // returns cinemaId's of all cinemas which are running a show for given movie
    // cinemaId's are ordered in ascending order
    public List<Integer> listCinemas(int movieId, int cityId) {
        return cinemaLister.listCinemas(movieId, cityId);
    }

    // returns all showId's of all shows displaying the movie in given cinema
    // in descending order of show startTime and then showId
    public List<Integer> listShows(int movieId, int cinemaId) {
        return showLister.listShows(movieId, cinemaId);
    }

}

class CinemaManager{
    private HashMap<Integer, Cinema> cache = new HashMap<>();
    public void addCinema(int cinemaId, int cityId,
       int screenCount, int screenRow, int screenColumn) {
       Cinema cinema = new Cinema(cinemaId, cityId,
               screenCount, screenRow, screenColumn);
       cache.put(cinemaId, cinema);
    }

    public Cinema getCinema(int cinemaId){
      return cache.get(cinemaId);
    }
}

class Cinema{
    private int cinemaId, cityId,
            screenCount,  screenRow,  screenColumn;

    public Cinema(int cinemaId, int cityId,
            int screenCount, int screenRow, int screenColumn) {
        this.cinemaId = cinemaId;
        this.cityId = cityId;
        this.screenCount = screenCount;
        this.screenRow = screenRow;
        this.screenColumn = screenColumn;
    }

    public int getCinemaId() {
        return cinemaId;
    }

    public int getCityId() {
        return cityId;
    }

    public int getScreenCount() {
        return screenCount;
    }

    public int getScreenRow() {
        return screenRow;
    }

    public int getScreenColumn() {
        return screenColumn;
    }
}

class ShowManager implements ShowSubject{
    private ArrayList<ShowObserver> observers
            = new ArrayList<>();
    private HashMap<Integer, Show> cache = new HashMap<>();
    Show addShow(int showId, int movieId, Cinema cinema,
                 int screenIndex, long startTime, long endTime){
        Show show = new Show(showId, movieId, screenIndex,
                startTime, endTime, cinema);
        cache.put(showId, show);
        notifyAll(show);
        return show;
    }
    
    Show getShow(int showId){
        return cache.get(showId);
    }

    public void addObserver(ShowObserver observer) {
     observers.add(observer);
    }

    public void notifyAll(Show show) {
      for(ShowObserver observer: observers)
          observer.update(show);
    }
}

class Show{
   private int showId, movieId, screenIndex;
   private long startTime, endTime;
   private Cinema cinema;

   public Show(int showId, int movieId, int screenIndex,
            long startTime, long endTime, Cinema cinema) {
        this.showId=showId;
        this.movieId = movieId;
        this.screenIndex = screenIndex;
        this.startTime = startTime;
        this.endTime = endTime;
        this.cinema = cinema;
   }

    @Override public String toString() {
        return "Show(" +
                "showId=" + showId +
                ", movieId=" + movieId +
                ", cinemaId=" + cinema.getCinemaId() +
                ", screenIndex=" + screenIndex +
                ')';
    }

    public int getShowId(){
       return showId;
    }
    
    public int getMovieId() {
        return movieId;
    }

    public int getScreenIndex() {
        return screenIndex;
    }

    public Long getStartTime() {
        return startTime;
    }

    public Cinema getCinema() {
        return cinema;
    }
}

class TicketBookingManager{
    // showId vs seats: row, column
    private HashMap<Integer, boolean[][]> seats = new HashMap();
    //showId vs free  seats
    private HashMap<Integer, Integer> freeSeatsCount = new HashMap();
    // ticketId vs booking data
    private HashMap<String, Booking> bookings= new HashMap<>();

    public List<String> bookTicket(String ticketId,
                 Show show, int ticketsCount) {
        ArrayList<String> ans = new ArrayList<>();
        // initializing seats and count for showId
        Cinema cinema = show.getCinema();;
        if (!seats.containsKey(show.getShowId())) {
            seats.put(show.getShowId(),
                    new boolean[cinema.getScreenRow()][cinema.getScreenColumn()]);
            freeSeatsCount.put(show.getShowId(),
                    cinema.getScreenRow() * cinema.getScreenColumn());
        }
        if (freeSeatsCount.get(show.getShowId())<ticketsCount) return ans;
        // update seats count
        freeSeatsCount.put(show.getShowId(), freeSeatsCount.get(show.getShowId())-ticketsCount);
        boolean showSeats[][]=seats.get(show.getShowId());
        // try to find continuous seats
        outer:
        for (int row = 0; row < cinema.getScreenRow(); row++)
           for(int column=0;column<cinema.getScreenColumn();column++){
               ans=lockContinuousFreeSeats(showSeats[row],
                       column, ticketsCount, row);
               if(ans.size()>0) break outer;
        }
        if(ans.size()==0)
        outer2:
        for (int row = 0; row < cinema.getScreenRow(); row++)
            for(int column=0;column<cinema.getScreenColumn();column++){
                if(ticketsCount<=0) break outer2;
                if(showSeats[row][column]) continue;
                ticketsCount--;
                showSeats[row][column]=true;
                ans.add(""+row+"-"+column);
        }
        Booking booking = new Booking(ticketId, show.getShowId(), ans);
        bookings.put(ticketId, booking);
        return ans;
    }
    
    private ArrayList<String> lockContinuousFreeSeats(
            boolean bookedSeats[], int start, int seatsCount, int row){
     ArrayList<String> booked= new ArrayList<>();
     if(start+seatsCount>bookedSeats.length) return booked;
     boolean hasSeats=true;
     for(int i=start;i<start+seatsCount;i++)
         if(bookedSeats[i]) hasSeats=false;
     if(!hasSeats) return booked;
    // System.out.println("start: "+start+", length:"+bookedSeats.length);
     for(int i=start;i<start+seatsCount;i++){
         bookedSeats[i]=true;
         booked.add(""+row+"-"+i);
     }
     return booked;
    }
    
    public boolean cancelTicket(String ticketId) {
        if(ticketId==null) return false;
        Booking booking = bookings.get(ticketId);
        if(booking==null || booking.isCancelled()) return false;
        boolean booked[][]=seats.get(booking.getShowId());
        if(booked==null) return false;
        booking.cancelBooking();
        for(String seat : booking.getSeats()){
          String position[]=seat.split("-");
          int row=Integer.parseInt(position[0]);
          int column=Integer.parseInt(position[1]);
          booked[row][column]=false;
        }
        freeSeatsCount.put(booking.getShowId(),
          freeSeatsCount.get(booking.getShowId())+booking.getSeats().size());
        return true;
    }

    public int getFreeSeatsCount(Show show) {
        Cinema cinema=show.getCinema();
        return freeSeatsCount.getOrDefault( show.getShowId(),
         cinema.getScreenRow()*cinema.getScreenColumn());
    }
}

class Booking{
    private String ticketId;
    /**bookingStatus =0 for booked, 1 for cancelled */
    private int showId, bookingStatus=0;
    private ArrayList<String> seats;

    public Booking(String ticketId, int showId, ArrayList<String> seats) {
        this.ticketId = ticketId;
        this.showId = showId;
        this.seats = seats;
    }

    boolean isCancelled(){
        return bookingStatus!=0;
    }

    void cancelBooking(){
        bookingStatus=1;
    }

    public String getTicketId() {
        return ticketId;
    }

    public int getShowId() {
        return showId;
    }

    public ArrayList<String> getSeats() {
        return seats;
    }
}

class ShowLister implements ShowObserver{
    private HashMap<String, ArrayList<Show>> cache = new HashMap<>();
    // returns all showId's of all shows displaying the movie in
    // given cinema. showId's are ordered in descending order
    // of startTime and then showId
    public List<Integer> listShows(int movieId, int cinemaId) {
        ArrayList<Integer> list = new ArrayList<>();
        ArrayList<Show> set = cache.get(""+movieId+"-"+cinemaId);
        if(set!=null) {
            set.sort((a,b)->{
                return a.getStartTime()!=b.getStartTime()?
                        b.getStartTime().compareTo(a.getStartTime())
                        : a.getShowId()-b.getShowId();
            });
            for(Show show:set)list.add(show.getShowId());
        }
       // System.out.println("movieId "+movieId+", cinemaId "+cinemaId+", list of shows "+list);
        return list;
    }

    public void update(Show show) {
        String key = ""+show.getMovieId()
                +"-"+show.getCinema().getCinemaId();
       // System.out.println("updating shows list: "+show);
        cache.putIfAbsent(key, new ArrayList<Show>());
        cache.get(key).add(show);
      //  System.out.println(cache.get(key));

    }
}

/*
class ShowLister implements ShowObserver{
    private HashMap<String, TreeSet<Show>> cache = new HashMap<>();
    // returns all showId's of all shows displaying the movie in
    // given cinema. showId's are ordered in descending order
    // of startTime and then showId
    public List<Integer> listShows(int movieId, int cinemaId) {
        ArrayList<Integer> list = new ArrayList<>();
        TreeSet<Show> set = cache.get(""+movieId+"-"+cinemaId);
        if(set!=null) for(Show show:set)list.add(show.getShowId());
        System.out.println("movieId "+movieId+", cinemaId "+cinemaId+", list of shows "+list);
        return list;
    }

    public void update(Show show) {
       String key = ""+show.getMovieId()
               +"-"+show.getCinema().getCinemaId();
       System.out.println("updating shows list: "+show);
       cache.putIfAbsent(key,
           new TreeSet<Show>((a,b)->{
           return a.getStartTime()!=b.getStartTime()?
                   b.getStartTime().compareTo(a.getStartTime())
                   : a.getShowId()-b.getShowId();
       }));
       cache.get(key).add(show);
       System.out.println(cache.get(key));
       
    }
} */

class CinemaLister implements ShowObserver{
    private HashMap<String, TreeSet<Integer>> cache = new HashMap<>();
    public void update(Show show) {
        String key = ""+show.getMovieId()
                +"-"+show.getCinema().getCityId();
        cache.putIfAbsent(key, new TreeSet<Integer>());
        cache.get(key).add(show.getCinema().getCinemaId());
    }
    
    /** returns cinemaId's of all cinemas which are running a show
     for given movie. cinemaId's are ordered in ascending order */
    public List<Integer> listCinemas(int movieId, int cityId) {
        ArrayList<Integer> list = new ArrayList<>();
        TreeSet set = cache.get(""+movieId+"-"+cityId);
        if(set!=null) list.addAll(set);
        return list;
    }
}


// uncomment below code when you are using your local code editor and
// comment it back again back when you are pasting completed solution in the online CodeZym editor
// this will help avoid unwanted compilation errors and get method autocomplete in your local code editor.
/**
 interface Q10MovieBookingInterface{
 void init(Helper10 helper);
 void addCinema(int cinemaId, int cityId,
       int screenCount, int screenRow, int screenColumn);
 void addShow(int showId, int movieId, int cinemaId,
       int screenIndex, long startTime, long endTime);
 List<String> bookTicket(String ticketId,
       int showId, int ticketsCount);
 boolean cancelTicket(String ticketId);
 int getFreeSeatsCount(int showId);
 // returns cinemaId's of all cinemas which are running a show for given movie
 // cinemaId's are ordered in ascending order
 List<Integer> listCinemas(int movieId, int cityId);
 // returns all showId's of all shows displaying the movie in given cinema
 // in descending order of show startTime and then showId
 List<Integer> listShows(int movieId, int cinemaId);

}

class Helper10{
 void print(String s){System.out.print(s);}
 void println(String s){print(s+"\n");}
}
*/