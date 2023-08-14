package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class Simulation {

    private final Random random = new Random();
    private int count = 0;
    private double currentTime;
    private double lambda;
    private final PriorityQueue<Event> eventQueue = new PriorityQueue<>();
    private final HashMap<String, Station> stationHashMap = new HashMap<>();
    private final ArrayList<Station> orderedList = new ArrayList<>();
    private Station entrance;
    private String title;
    private Function<Integer, String> guiTextOutput;
    private static ObjectMapper objectMapper = new ObjectMapper();

    public void fireNextEvent() {
        Event event = eventQueue.poll();
        currentTime = event != null ? event.timestamp : 0.0;
        handleEvent(event);
    }

    public String peekNextEvent() {
        return eventQueue.isEmpty() ? "" : eventQueue.peek().toString();
    }

    public static Simulation jsonParser(String jsonData) throws JsonProcessingException {
        Simulation sim = new Simulation();
        var rootNode = objectMapper.readTree(jsonData);
        sim.title = rootNode.get("name").asText();
        sim.lambda = rootNode.get("lambda").asDouble();
        var entryNode = rootNode.get("entrance");
        String entranceName = entryNode.get("name").asText();
        HashMap<String, Double> map = new HashMap<>();
        double baseValue = 0.0;
        for (var exit : entryNode.get("exits")) {
            double p = exit.get("p").asDouble() + baseValue;
            map.put(exit.get("exitname").asText(), p);
            baseValue = p;
        }
        for (var mapEntry : map.entrySet()) {
            map.put(mapEntry.getKey(), mapEntry.getValue() / baseValue);
        }
        final var sortedList = new ArrayList<>(map.entrySet());
        sortedList.sort(Map.Entry.comparingByValue());
        Supplier<String> supplier = () -> {
            double r = sim.random.nextDouble();
            for (var entry : sortedList) {
                if (r <= entry.getValue())
                    return entry.getKey();
            }
            return null;
        };
        sim.addEntrance(sim.new Station(entranceName, 0, supplier, Integer.MAX_VALUE));

        for (var station : rootNode.get("stations")) {
            String stationName = station.get("name").asText();
            double mu = 0.0;
            if (station.get("mu") != null && station.get("mu").isDouble()) {
                mu = station.get("mu").asDouble();
            }
            map = new HashMap<>();
            baseValue = 0.0;
            if (station.get("exits") != null) {
                for (var exit : station.get("exits")) {
                    double p = exit.get("p").asDouble() + baseValue;
                    map.put(exit.get("exitname").asText(), p);
                    baseValue = p;
                }
            }
            for (var mapEntry : map.entrySet()) {
                map.put(mapEntry.getKey(), mapEntry.getValue() / baseValue);
            }
            final var sortedExits = new ArrayList<>(map.entrySet());
            sortedExits.sort(Map.Entry.comparingByValue());
            supplier = () -> {
                double r = sim.random.nextDouble();
                for (var entry : sortedExits) {
                    if (r <= entry.getValue())
                        return entry.getKey();
                }
                return null;
            };
            int maxServiceSize = Integer.MAX_VALUE;
            if (station.get("maxServiceSize") != null && station.get("maxServiceSize").isInt()) {
                maxServiceSize = station.get("maxServiceSize").asInt();
            }
            sim.add(sim.new Station(stationName, mu, supplier, maxServiceSize));
        }
        sim.guiTextOutput = sim.getDefaultOutputFunction();
        return sim;
    }

    public String getStatusHTMLOutput(int fontSize) {
        return guiTextOutput.apply(fontSize);
    }

    public String getTitle() {
        return title;
    }

    private String getNextId() {
        return "K_" + (++count);
    }

    private void add(Station station) {
        stationHashMap.put(station.name, station);
        orderedList.add(station);
    }

    private void addEntrance(Station entryStation) {
        entrance = entryStation;
        eventQueue.add(new Event(entrance, null, 0, getNextId()));
        add(entryStation);
    }


    private Function<Integer, String> getDefaultOutputFunction() {
        return (fontSize) -> {
            String text = "<b><p style=\"font-size:" + fontSize + "px;\">Time: "
                    + timeStampToMinuteAndSeconds(currentTime) + "</p>";
            for (var station : orderedList) {
                text += buildOutputLine(station, fontSize);
            }
            return text + "</b>";
        };
    }

    private static String buildOutputLine(Station station, int fontSize) {
        String text = "<p style=\"font-size:" + fontSize + "px;\">" + station.name + ": " + station.serviceDesk;
        if (station.mu > 0) {
            text += "  wartend: " + station.localQueue;
        }
        return text + "</p>";
    }

    private static String timeStampToMinuteAndSeconds(double timestamp) {
        long minutes = (long) timestamp;
        double remainder = (timestamp - minutes) * 100;
        long seconds = (long) (remainder / 100 * 60);
        String remainderString = String.valueOf(seconds);
        if (remainderString.length() < 2)
            remainderString = "0" + remainderString;
        return minutes + ":" + remainderString;
    }

    private void handleEvent(Event event) {
        System.out.println(event);
        Station current = event.current;
        if (!current.localQueue.contains(event.customerId)) {
            current.localQueue.addLast(event.customerId);
        }
        if (event.previous != null) {
            Station previousStation = event.previous;
            previousStation.serviceDesk.remove(event.customerId);
            if (!previousStation.localQueue.isEmpty()) {
                Event newEvent = new Event(previousStation, null, event.timestamp, previousStation.localQueue.get(0));
                eventQueue.add(newEvent);
            }
        }
        if (current.serviceDesk.size() < event.current.maxServiceSize) {
            current.serviceDesk.add(event.customerId);
            current.localQueue.remove(event.customerId);
            Station nextStation = current.nextStation();
            if (nextStation != null) {
                double nextEventTime = current.mu > 0.0 ? event.timestamp + exponentialRNG(current.mu) : event.timestamp;
                System.out.println(current.name + " creating new Event at " + nextStation.name + ", "
                        + timeStampToMinuteAndSeconds(nextEventTime) + " with " + event.customerId);
                Event newEvent = new Event(nextStation, current, nextEventTime, event.customerId);
                eventQueue.add(newEvent);
            }
        } else {
            System.out.println(event.customerId + " waiting at " + current.name);
        }
        if (current == entrance) {
            double nextEventTime = event.timestamp + exponentialRNG(lambda);
            eventQueue.add(new Event(entrance, null, nextEventTime, getNextId()));
        }
    }

    private double exponentialRNG(double lambda) {
        return Math.log(1 - random.nextDouble()) / (-lambda);
    }


    public static Simulation buildKaeseLaden() {
        Simulation sim = new Simulation();
        sim.title = "Käseladen";
        sim.lambda = 100.0 / 60.0;
        Station eingang = sim.new Station("Eingang", 0, () -> sim.random.nextDouble() > 0.1 ? "Regal" : "Theke", Integer.MAX_VALUE);
        sim.addEntrance(eingang);
        Station regal = sim.new Station("Regal", 30.0 / 60.0, () -> sim.random.nextDouble() < 1.0 / 9.0 ? "Theke" : "Kasse", Integer.MAX_VALUE);
        sim.add(regal);
        Station theke = sim.new Station("Theke", 25.0 / 60.0, () -> "Kasse", 1);
        sim.add(theke);
        Station kasse = sim.new Station("Kasse", 40.0 / 60.0, () -> "Ausgang", 3);
        sim.add(kasse);
        Station ausgang = sim.new Station("Ausgang", 0, () -> null, Integer.MAX_VALUE);
        sim.add(ausgang);
        sim.guiTextOutput = sim.getDefaultOutputFunction();
        return sim;
    }

    public static Simulation buildKaeseLadenMitProbierstand() {
        Simulation sim = new Simulation();
        sim.title = "Käseladen mit Probierstand";
        sim.lambda = 100.0 / 60.0;
        Station eingang = sim.new Station("Eingang", 0,
                () -> sim.random.nextDouble() < 0.3 ? "Probierstand" : sim.random.nextDouble() > 1.0 / 9.0 ? "Regal" : "Theke", Integer.MAX_VALUE);
        sim.addEntrance(eingang);
        Station probierstand = sim.new Station("Probierstand", 40.0 / 60,
                () -> sim.random.nextDouble() < 0.2 ? "Kasse" : sim.random.nextDouble() < 0.7 ? "Regal" : "Theke", 2);
        sim.add(probierstand);
        Station regal = sim.new Station("Regal", 30.0 / 60.0, () -> sim.random.nextDouble() < 1.0 / 9.0 ? "Theke" : "Kasse", Integer.MAX_VALUE);
        sim.add(regal);
        Station theke = sim.new Station("Theke", 25.0 / 60.0, () -> "Kasse", 1);
        sim.add(theke);
        Station kasse = sim.new Station("Kasse", 40.0 / 60.0, () -> "Ausgang", 3);
        sim.add(kasse);
        Station ausgang = sim.new Station("Ausgang", 0, () -> null, Integer.MAX_VALUE);
        sim.add(ausgang);
        sim.guiTextOutput = sim.getDefaultOutputFunction();
        return sim;
    }


    private class Station {
        final String name;
        final double mu;
        final Supplier<String> getExitStationId;
        final int maxServiceSize;
        final LinkedList<String> localQueue = new LinkedList<>();
        final LinkedList<String> serviceDesk = new LinkedList<>();

        public Station nextStation() {
            return stationHashMap.get(getExitStationId.get());
        }

        Station(String name, double mu, Supplier<String> getExitStationId, int maxServiceSize) {
            this.name = name;
            this.mu = mu;
            this.getExitStationId = getExitStationId;
            this.maxServiceSize = maxServiceSize;
        }

    }

    private class Event implements Comparable<Event> {
        final Station current;
        final Station previous;
        final double timestamp;
        final String customerId;

        Event(Station current, Station previous, double timestamp, String customerId) {
            this.current = current;
            this.previous = previous;
            this.timestamp = timestamp;
            this.customerId = customerId;
        }

        @Override
        public int compareTo(Event o) {
            return Double.compare(timestamp, o.timestamp);
        }

        @Override
        public String toString() {
            return "Event{" +
                    "current=" + current.name +
                    ", previous=" + (previous != null ? previous.name : "") +
                    ", timestamp=" + timeStampToMinuteAndSeconds(timestamp) +
                    ", customerId='" + customerId + '\'' +
                    '}';
        }
    }


}
