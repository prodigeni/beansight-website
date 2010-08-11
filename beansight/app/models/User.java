package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;

import exceptions.UserIsAlreadyFollowingInsightException;

import models.Vote.State;

import play.db.jpa.Model;
import play.libs.Crypto;

@Entity
public class User extends Model {

	public String userName;
	public String firstName;
	public String lastName;
	public String password;
	public String email;
	
	/** list of insights created by this user */
	@OneToMany(mappedBy="creator", cascade=CascadeType.ALL)
	public List<Insight> createdInsights;
	
	/** every votes of the current user */
	@OneToMany(mappedBy="user", cascade = CascadeType.ALL)
	public List<Vote> votes;

	/** the insights followed by this user */
	@ManyToMany(cascade = CascadeType.ALL)
	public List<Insight> followedInsights;
	
	
    public User(String email, String userName, String password) {
        this.email = email;
        this.password = Crypto.passwordHash(password);
        this.userName = userName;
        this.votes = new ArrayList<Vote>();
        this.createdInsights = new ArrayList<Insight>();
        this.followedInsights = new ArrayList<Insight>();
    }

    public String toString() {
        return userName;
    }
    
    /**
     * Call this method to authenticate a user given his username 
     * and password.
     * 
     * @param username
     * @param password
     * @return true if authenticated, false otherwise
     */
    public static boolean connect(String username, String password) {
    	User user = find("userName=? and password=?", username, Crypto.passwordHash(password)).first();
    	if (user!=null) {
    		return true;
    	}

    	return false;
    }
	
    /**
     * Static method to get a User instance given his username
     * 
     * @param userName
     * @return
     */
    public static User findByUserName(String userName) {
    	return find("userName = ?", userName).first();
    }

    /**
     * Call this method to create a new insight that will be
     * automatically owned by the current user.
     * 
     * TODO : get the end date.
     * 
     * @param insightContent
     * @return
     */
    public Insight createInsight(String insightContent) {
    	Date endDate = new Date();
    	Insight i = new Insight(this, insightContent, endDate);
    	this.createdInsights.add(i);
    	this.save();
    	
    	return i;
    }
    
    /**
     * Call this method to set a vote for one insight for the
     * current user.
     * It shouldn't be possible to vote twice for one insight.
     * 
     * TODO : check user hasn't already vote for the insight
     * 
     * @param insightId : id of the insight user is voting for.
     * @param voteState State.AGREE or State.DISAGREE
     */
	public void voteToInsight(Long insightId, State voteState) {
		Insight insight = Insight.findById(insightId);
		Vote vote = new Vote(this, insight, voteState);
		votes.add(vote);
		if (voteState.equals(State.AGREE)) {
			insight.agreeCount++;
		} else {
			insight.disagreeCount++;
		}
		insight.votes.add(vote);
		insight.save();
		save();
	}

	public void tag(Insight insight, String label) {
		Tag tag = new Tag(this, insight, label);
		insight.tags.add(tag);
		insight.save();
	}

	
	public boolean isFollowingInsight(Insight insight) {
		if(followedInsights.contains(insight))
			return true;
		return false;
	}
	
	/**
	 * 
	 * @param insightId
	 */
	public void startFollowingThisInsight(Long insightId) throws UserIsAlreadyFollowingInsightException {
		Insight insight = Insight.findById(insightId);
		
		// If we are already following the insight throw a business exception
		if (isFollowingInsight(insight)==true)
			throw new UserIsAlreadyFollowingInsightException();
		
		followedInsights.add(insight);
		save();
		insight.followers.add(this);
		insight.save();
	}
	
	
	/**
	 * 
	 * @param insightId
	 */
	public void stopFollowingThisInsight(Long insightId) {
		Insight insight = Insight.findById(insightId);
		
		// If we were not following the insight just do nothing ...
		if (isFollowingInsight(insight)==false)
			return;
		
		followedInsights.remove(insight);
		save();
		insight.followers.remove(this);
		insight.save();
	}
}
