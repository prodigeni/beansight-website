package controllers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import models.Category;
import models.Insight;
import models.User;
import models.Vote;
import models.Vote.State;
import play.Play;
import play.db.jpa.FileAttachment;
import play.libs.Files;
import play.libs.Images;
import play.modules.search.Search;
import play.modules.search.Search.Query;
import play.mvc.Controller;
import exceptions.CannotVoteTwiceForTheSameInsightException;
import exceptions.UserIsAlreadyFollowingInsightException;

public class Application extends Controller {

	public static void index() {
		List<Insight> insights = Insight.findAll();
		
		if(Security.isConnected()) {
			render("Application/indexConnected.html", insights);
		}
		
		render("Application/indexNotConnected.html", insights);
	}
	
	public static void create() {
		render();
	}

	public static void favorites() {
		render();
	}

	public static void insights() {
		render();
	}

	public static void experts() {
		render();
	}
	/**
	 * create an insight for the current user
	 * @param insightContent: the content of this insight
	 * @param endDate: the end date chosen by the user
	 * @param tagLabelList: a comma separated list of tags
	 * @param categoryId: the ID of the category of the insight
	 */
	public static void createInsight(String insightContent, Date endDate, String tagLabelList, long categoryId) {
		User currentUser = CurrentUser.getCurrentUser();
		Insight insight = currentUser.createInsight(insightContent, endDate, tagLabelList, categoryId);
		
		index();
	}
	
	public static void displayAllInsights() {
		List<Insight> allInsights = Insight.all().fetch();
		render(allInsights);
	}
	
	
	/**
	 * Agree a given insight
	 * 
	 * @param insightId
	 */
	public static void agree(Long insightId) {
		// TODO : if not connected: go to log / signin page
		User currentUser = CurrentUser.getCurrentUser();
		try {
			currentUser.voteToInsight(insightId, State.AGREE);
		} catch (CannotVoteTwiceForTheSameInsightException e) {
			// TODO : add a message on the UI ?
		}

		Insight insight = Insight.findById(insightId);
		render("Application/vote.json", insight);
	}

	/**
	 * Disagree a given insight
	 * 
	 * @param insightId
	 */
	public static void disagree(Long insightId) {
		// TODO : if not connected: go to log / signin page
		User currentUser = CurrentUser.getCurrentUser();

		try {
			currentUser.voteToInsight(insightId, State.DISAGREE);
		} catch (CannotVoteTwiceForTheSameInsightException e) {
			// TODO : add a message on the UI ?
		}

		Insight insight = Insight.findById(insightId);
		render("Application/vote.json", insight);
	}
	
	/**
	 * Show info about a given insight
	 * @param id
	 */
    public static void showInsight(Long id) {
        Insight insight = Insight.findById(id);
        notFoundIfNull(insight);
		User currentUser = CurrentUser.getCurrentUser();
        Vote lastUserVote = Vote.findLastVoteByUserAndInsight(currentUser.id, id);
        
        render(insight, currentUser, lastUserVote);
    }
    
    /**
     * Show info about a given user
     * @param id
     */
    public static void showUser(Long id) {
    	User user = User.findById(id);
        notFoundIfNull(user);              
    	render(user);
    }
    
    public static void startFollowingInsight(Long insightId) {
		User currentUser = CurrentUser.getCurrentUser();
    	try {
			currentUser.startFollowingThisInsight(insightId);
		} catch (UserIsAlreadyFollowingInsightException e) {
			flash.error(e.getMessage());
		}
		
    	index();
    }

    public static void stopFollowingInsight(Long insightId) {
		User currentUser = CurrentUser.getCurrentUser();
		currentUser.stopFollowingThisInsight(insightId);
    	index();
    }
    
    /**
     * Add a comment to a specific insight for the current user
     * @param insightId: id of the insight
     * @param content: text content of the insight
     */
    public static void addComment(Long insightId, String content) {
		User currentUser = CurrentUser.getCurrentUser();
    	Insight insight = Insight.findById(insightId);
    	insight.addComment(content, currentUser);
    	showInsight(insightId);
    }
    
    /**
     * add tags to an insight
     * @param insightId : the id of the tagged insight
     * @param tagLabelList: a comma separated list of tag labels
     */
	public static void addTags(Long insightId, String tagLabelList) {
		User currentUser = CurrentUser.getCurrentUser();
		Insight insight = Insight.findById(insightId);
    	insight.addTags(tagLabelList, currentUser);
		showInsight(insightId);
	}
	
	/**
	 * Get a list of all the categories of the website	
	 */
	public static List<Category> getCategories() {
		List<Category> categories = Category.findAll();
		return categories;
	}
	
	// TODO remove me, score computation should be called in a job.
	public static void temp_recomputeScore() {
		User currentUser = CurrentUser.getCurrentUser();
    	currentUser.computeScores();
    	currentUser.save();
    	showUser(currentUser.id);
	}

	
	public static void saveSettings(User user) {
		User currentUser = CurrentUser.getCurrentUser();
		// User should be the same as the one connected
	    if (currentUser.id.equals(user.id)==false) {
	    	forbidden("It seems you are trying to hack someone else settings");
	    }
		currentUser.avatar = user.avatar;
		currentUser.save();
		settings();
	}
	
	
	public static void settings() {
		User user = CurrentUser.getCurrentUser();
		render(user);
	}
	
	/**
	 * Render the user avatar 
	 * @param userId
	 */
	public static void showAvatar(Long userId) {
		User user = User.findById(userId);
		if (user != null && user.avatar.isSet()) {
			renderBinary(user.avatar.get());
		} 
		renderBinary(new File("public/images/unknown.jpg"));
		notFound();
	}
	
	/**
	 * Receive an image from which the user want to crop his avatar.
	 * The image is stored in the default attachments path with the name "tmpFile"+user.id
	 * 
	 * @param file
	 */
	public static void uploadUncropedAvatarImage(File file) {
		User user = CurrentUser.getCurrentUser();
		
		File to = new File(FileAttachment.getStore(), "tmpFile"+user.id);
		Files.copy(file, to);

		uploadAndCropAvatarScreen();
	}
	
	/**
	 * Render the uploaded image so that the user crop his avatar from it 
	 */
	public static void displayUploadedTmpImage() {
		User user = CurrentUser.getCurrentUser();
		File tmpFile = new File(FileAttachment.getStore(), "tmpFile"+user.id);
		if (!tmpFile.exists()) {
			notFound();
		}
		renderBinary(tmpFile);
	}	
	
	/**
	 * Render to the page which give the opportunity to upload an image to crop
	 */
	public static void uploadAndCropAvatarScreen() {
		User user = CurrentUser.getCurrentUser();
		
		render(user);
	}
	
	public static void cropImage(Integer x1, Integer y1, Integer x2, Integer y2, Integer imageW, Integer imageH) {
		User user = CurrentUser.getCurrentUser();
		
		File imageToCrop = new File(FileAttachment.getStore(), "tmpFile"+user.id);
		try {
			BufferedImage source = ImageIO.read(imageToCrop);
			int originalImageWidth = source.getWidth();
			int originalImageHeight = source.getHeight();
			float ratioX = new Float(originalImageWidth) / imageW;
			float ratioY = new Float(originalImageHeight) / imageH;
		
			Images.crop(imageToCrop, imageToCrop, Math.round(x1*ratioX), Math.round(y1*ratioY), Math.round(x2*ratioX), Math.round((y2*ratioY)));
			user.avatar.set(imageToCrop);
			user.saveAttachment();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public static void search(String query) {
		//TODO Steren : this query string construction is temporary, we should better handle this
		String fullQueryString = "content:" + query + " OR tags:" + query + " OR category:" + query;
		Query q = Search.search(fullQueryString, Insight.class);
		List<Insight> insights = q.fetch();
		render(query, insights);
	}
	
	public static void userSearch(String query) {
		Query q = Search.search(query, User.class);
		List<User> users = q.fetch();
		render(users);
	}
	
	
}