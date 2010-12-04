package jobs;

import java.util.Date;
import java.util.List;

import notifiers.Mails;

import models.Insight;
import models.MailConfirmTask;
import models.Trend;
import play.Logger;
import play.jobs.Every;
import play.jobs.Job;

@Every("15s")
public class MailConfirmationJob extends Job {
	
    @Override
    public void doJob() throws Exception {
    	MailConfirmTask task = MailConfirmTask.find("sent is false and attempt < 5").first();
    	// TODO : do not take only the first one, but take a certain number, and send the emails with a delay between each sending.
    	if(task != null) {
    		task.attempt++;
	    	Mails.confirmation(task);
	    	task.sent = true;
			task.save();
    	}
    	
    }
}