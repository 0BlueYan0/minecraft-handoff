package tw.handoff.relay.core;
import java.util.*;
/** Bounded between the network and game threads. Coalesce only adjacent continuous input. */
public final class InputInbox {
 private record Entry(String value,boolean replaceable){}
 private final ArrayDeque<Entry> queue=new ArrayDeque<>();private final int capacity;
 public InputInbox(int capacity){if(capacity<1)throw new IllegalArgumentException();this.capacity=capacity;}
 public synchronized boolean offer(String value,boolean replaceable){
  if(value==null||value.length()>22100)return false;
  if(replaceable&&!queue.isEmpty()&&queue.peekLast().replaceable())queue.removeLast();
  if(queue.size()>=capacity)return false;queue.addLast(new Entry(value,replaceable));return true;
 }
 public synchronized List<String> drain(){var values=queue.stream().map(Entry::value).toList();queue.clear();return values;}
}
