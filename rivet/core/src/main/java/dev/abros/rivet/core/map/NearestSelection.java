package dev.abros.rivet.core.map;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** Stable nearest-k selection; evaluates each eligible distance once and retains at most k entries. */
public final class NearestSelection {
 private record Ranked<T>(T value,double distance,int ordinal){}
 public static <T> List<T> select(List<T> values,int limit,Predicate<? super T> eligible,ToDoubleFunction<? super T> distance){
  if(limit<0)throw new IllegalArgumentException("Negative selection limit");if(limit==0||values.isEmpty())return List.of();
  Comparator<Ranked<T>> order=Comparator.<Ranked<T>>comparingDouble(Ranked::distance).thenComparingInt(Ranked::ordinal);
  var nearest=new PriorityQueue<Ranked<T>>(Math.min(limit,values.size()),order.reversed());int ordinal=0;
  for(T value:values){int index=ordinal++;if(!eligible.test(value))continue;double d=distance.applyAsDouble(value);
   if(nearest.size()<limit)nearest.add(new Ranked<>(value,d,index));
   // A later equal-distance candidate must not displace the earlier one.
   else if(Double.compare(d,nearest.peek().distance())<0){nearest.poll();nearest.add(new Ranked<>(value,d,index));}
  }
  var result=new ArrayList<>(nearest);result.sort(order);return result.stream().map(Ranked::value).toList();
 }
 private NearestSelection(){}
}
