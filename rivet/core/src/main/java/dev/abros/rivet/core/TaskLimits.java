package dev.abros.rivet.core;
/** Creation limits; lowering them never removes existing task data. */
public record TaskLimits(int perOwner,int subtasks,int comments) {
    public static TaskLimits defaults(){return new TaskLimits(200,30,100);}
    public TaskLimits {if(perOwner<0||subtasks<0||comments<0)throw new IllegalArgumentException("Invalid task limits");}
    public boolean allowsTask(int count){return perOwner==0||count<perOwner;}
    public boolean allowsSubtask(int count){return subtasks==0||count<subtasks;}
    public boolean allowsComment(int count){return comments==0||count<comments;}
}
